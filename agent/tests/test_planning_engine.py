"""编排引擎的单元测试：分层并行 / AND-OR 放行 / 三层恢复 / 模板渲染。"""
import asyncio

import pytest

from app.planning import (
    ExecContext,
    ExecResult,
    PlanEngine,
    PlanGraph,
    PlanNode,
    PlanningError,
    RecoveryPolicy,
    render_description,
)
from app.planning.scheduler import topo_layers
from app.planning.validate import ensure_valid_plan

# 场景：s1/s2 并行 → s3(AND) → s4
BASE_JSON = """
{
  "goal": "备份照片并分享",
  "nodes": [
    {"id": "s1", "description": "搜索 jpg", "worker_group": "search"},
    {"id": "s2", "description": "创建备份目录", "worker_group": "file_ops"},
    {"id": "s3", "description": "移动 {{s1.file_ids}} 到 {{s2.dir_id}}",
     "worker_group": "file_ops", "join_policy": "all"},
    {"id": "s4", "description": "分享 sunset.jpg", "worker_group": "share"}
  ],
  "edges": [
    {"src": "s1", "dst": "s3"},
    {"src": "s2", "dst": "s3"},
    {"src": "s3", "dst": "s4"}
  ]
}
"""


def base_plan() -> PlanGraph:
    return ensure_valid_plan(PlanGraph.model_validate_json(BASE_JSON))


class ScriptedPlanner:
    """按脚本返回计划的 Planner 替身（不依赖真实 LLM）。"""

    def __init__(self, plans: list[PlanGraph | None]):
        self._plans = list(plans)
        self.feedbacks: list[str] = []
        self.replan_calls = 0
        self.decompose_calls = 0

    async def plan(self, goal: str, feedback: str = "") -> PlanGraph | None:
        self.feedbacks.append(feedback)
        if not self._plans:
            return None
        plan = self._plans.pop(0)
        return plan

    async def replan_node(self, node: PlanNode, error: str) -> PlanNode | None:
        self.replan_calls += 1
        return None

    async def decompose(self, node: PlanNode, error: str) -> PlanGraph | None:
        self.decompose_calls += 1
        return None


class ScriptedExecutor:
    """按 node_id → 结果序列返回的执行器替身。

    每个条目是一次调用的结果；耗尽后重复最后一个。
    """

    def __init__(self, script: dict[str, list[ExecResult]], delay: float = 0.01):
        self._script = script
        self._delay = delay
        self.calls: list[tuple[str, int]] = []
        self.descriptions: dict[str, str] = {}

    async def __call__(self, node: PlanNode, ctx: ExecContext) -> ExecResult:
        self.calls.append((node.id, ctx.attempt))
        idx = len([c for c in self.calls if c[0] == node.id]) - 1
        results = self._script.get(node.id) or [ExecResult(ok=True, output="完成")]
        chosen = results[min(idx, len(results) - 1)]
        self.descriptions[node.id] = ctx.description
        await asyncio.sleep(self._delay)
        return chosen


def ok(output: str = "完成", **data) -> ExecResult:
    return ExecResult(ok=True, output=output, data=data)


def fail(error: str, transient: bool = False) -> ExecResult:
    return ExecResult(ok=False, error=error, transient=transient)


def _no_sleep():
    async def _noop(_seconds: float) -> None:
        return None

    return _noop


# --------------------------------------------------------------------------- 分层


def test_topo_layers_put_independent_nodes_together():
    assert topo_layers(base_plan()) == [["s1", "s2"], ["s3"], ["s4"]]


async def test_engine_runs_all_nodes():
    engine = PlanEngine(ScriptedPlanner([base_plan()]), ScriptedExecutor({}), sleep=_no_sleep())
    report = await engine.run("备份照片并分享")
    assert report.completed
    assert set(report.outcomes) == {"s1", "s2", "s3", "s4"}


async def test_layer_runs_in_parallel_not_sequential():
    """同一层的两个节点应并发：总耗时接近单个节点而非两者之和。"""
    executor = ScriptedExecutor({}, delay=0.15)
    engine = PlanEngine(ScriptedPlanner([base_plan()]), executor, sleep=_no_sleep())
    elapsed = await _timed(engine.run("备份照片并分享"))
    # 4 层串行 ≈ 0.6s；首层并行后 ≈ 0.45s
    assert elapsed < 0.6, elapsed


async def _timed(coro) -> float:
    loop = asyncio.get_running_loop()
    start = loop.time()
    await coro
    return loop.time() - start


# --------------------------------------------------------------------------- 依赖放行


async def test_and_join_skips_downstream_when_predecessor_failed():
    """AND join 下前驱失败 → 下游 skipped，但不影响兄弟分支（局部失败不阻塞）。"""
    executor = ScriptedExecutor(
        {
            "s2": [fail("目录已存在", transient=False)],
        }
    )
    engine = PlanEngine(
        ScriptedPlanner([base_plan()]),
        executor,
        policy=RecoveryPolicy(max_retries=0, replan_attempts=0, decompose_attempts=0),
        sleep=_no_sleep(),
    )
    report = await engine.run("备份照片并分享")
    assert report.outcomes["s2"].status == "failed"
    assert report.outcomes["s3"].status == "skipped"
    assert report.outcomes["s4"].status == "skipped"
    # 兄弟分支 s1 照常执行成功
    assert report.outcomes["s1"].status == "ok"
    assert not report.completed


async def test_or_join_proceeds_when_one_predecessor_ok():
    """OR join：任一前驱成功即可放行。"""
    plan = PlanGraph.model_validate_json(
        """
        {"goal":"g","nodes":[
            {"id":"a","description":"路径A"},
            {"id":"b","description":"路径B"},
            {"id":"c","description":"汇总","join_policy":"any"}
        ],
        "edges":[{"src":"a","dst":"c"},{"src":"b","dst":"c"}]}
        """
    )
    ensure_valid_plan(plan)
    executor = ScriptedExecutor({"a": [fail("A 失败")], "b": [ok("B 成功")]})
    engine = PlanEngine(
        ScriptedPlanner([plan]),
        executor,
        policy=RecoveryPolicy(max_retries=0, replan_attempts=0, decompose_attempts=0),
        sleep=_no_sleep(),
    )
    report = await engine.run("g")
    assert report.outcomes["a"].status == "failed"
    assert report.outcomes["c"].status == "ok", report.outcomes["c"].note


async def test_or_join_skips_when_all_predecessors_failed():
    plan = PlanGraph.model_validate_json(
        """
        {"goal":"g","nodes":[
            {"id":"a","description":"路径A"},
            {"id":"b","description":"路径B"},
            {"id":"c","description":"汇总","join_policy":"any"}
        ],
        "edges":[{"src":"a","dst":"c"},{"src":"b","dst":"c"}]}
        """
    )
    executor = ScriptedExecutor({"a": [fail("A 失败")], "b": [fail("B 失败")]})
    engine = PlanEngine(
        ScriptedPlanner([plan]),
        executor,
        policy=RecoveryPolicy(max_retries=0, replan_attempts=0, decompose_attempts=0),
        sleep=_no_sleep(),
    )
    report = await engine.run("g")
    assert report.outcomes["c"].status == "skipped"


# --------------------------------------------------------------------------- 三层恢复


async def test_layer1_retry_recovers_transient_error():
    executor = ScriptedExecutor({"s1": [fail("接口超时", transient=True), ok("重试后成功")]})
    engine = PlanEngine(ScriptedPlanner([base_plan()]), executor, sleep=_no_sleep())
    report = await engine.run("备份照片并分享")
    assert report.outcomes["s1"].status == "ok"
    assert report.outcomes["s1"].stage == "retry"
    assert report.outcomes["s1"].attempts == 2


async def test_non_transient_error_does_not_retry():
    """确定性失败不应浪费重试次数，直接升级到 Layer2/3。"""
    executor = ScriptedExecutor({"s1": [fail("参数非法", transient=False), ok("不可能到这里")]})
    planner = ScriptedPlanner([base_plan()])
    engine = PlanEngine(
        planner, executor, policy=RecoveryPolicy(replan_attempts=0, decompose_attempts=0), sleep=_no_sleep()
    )
    report = await engine.run("备份照片并分享")
    assert report.outcomes["s1"].status == "failed"
    assert executor.calls.count(("s1", 1)) == 1


class ReplanningPlanner(ScriptedPlanner):
    async def replan_node(self, node: PlanNode, error: str) -> PlanNode | None:
        self.replan_calls += 1
        return node.model_copy(update={"description": f"{node.description}（改用逐条处理）"})


async def test_layer2_replan_changes_approach():
    # 第 1 次：原始描述失败；第 2 次（replan 后的新表述）成功
    executor = ScriptedExecutor({"s4": [fail("批量接口不支持"), ok("改用单条接口后成功")]})
    planner = ReplanningPlanner([base_plan()])
    engine = PlanEngine(
        planner, executor, policy=RecoveryPolicy(max_retries=0, replan_attempts=1), sleep=_no_sleep()
    )
    report = await engine.run("备份照片并分享")
    assert report.outcomes["s4"].status == "ok"
    assert report.outcomes["s4"].stage == "replan"
    assert planner.replan_calls == 1


class DecomposingPlanner(ScriptedPlanner):
    async def replan_node(self, node: PlanNode, error: str) -> PlanNode | None:
        self.replan_calls += 1
        return None  # Layer2 放弃，交给 Layer3

    async def decompose(self, node: PlanNode, error: str) -> PlanGraph | None:
        self.decompose_calls += 1
        return PlanGraph(
            goal=f"拆解 {node.id}",
            nodes=[
                PlanNode(id="d1", description=f"{node.description}：先取得 fileId"),
                PlanNode(id="d2", description=f"{node.description}：再执行操作"),
            ],
            edges=[{"src": "d1", "dst": "d2"}],
        )


async def test_layer3_decompose_bypasses_blocked_node():
    executor = ScriptedExecutor(
        {
            "s4": [fail("权限不足，无法一次性完成")],
            "s4::d1": [ok("拿到 fileId", file_id="f-9")],
            "s4::d2": [ok("分步执行完成")],
        }
    )
    planner = DecomposingPlanner([base_plan()])
    engine = PlanEngine(
        planner,
        executor,
        policy=RecoveryPolicy(max_retries=0, replan_attempts=1, decompose_attempts=1),
        sleep=_no_sleep(),
    )
    report = await engine.run("备份照片并分享")
    assert planner.decompose_calls == 1
    assert report.outcomes["s4"].status == "ok"
    assert report.outcomes["s4"].stage == "decompose"
    # 子图明细挂在父节点的 children 上：上层视图仍是原 DAG，同时可追溯
    children = report.outcomes["s4"].children
    assert set(children) == {"s4::d1", "s4::d2"}
    assert all(c.status == "ok" for c in children.values())
    # 子节点的产出会合并给父节点
    assert report.outcomes["s4"].data["file_id"] == "f-9"


async def test_exhausted_failure_does_not_block_siblings():
    executor = ScriptedExecutor({"s2": [fail("彻底失败")]})
    planner = ScriptedPlanner([base_plan()])
    engine = PlanEngine(
        planner, executor, policy=RecoveryPolicy(max_retries=0, replan_attempts=0, decompose_attempts=0), sleep=_no_sleep()
    )
    report = await engine.run("备份照片并分享")
    assert report.outcomes["s2"].status == "failed"
    assert report.outcomes["s1"].status == "ok"
    assert "汇总" in report.render_summary()


# --------------------------------------------------------------------------- 规划与体检联动


async def test_invalid_plan_is_replanned_with_feedback():
    """首个计划带环 → 体检拦截 → 把结论反馈给 Planner → 第二个计划通过。"""
    cyclic = PlanGraph.model_validate_json(
        """
        {"goal":"g","nodes":[{"id":"a","description":"x"},{"id":"b","description":"y"}],
         "edges":[{"src":"a","dst":"b"},{"src":"b","dst":"a"}]}
        """
    )
    good = PlanGraph.model_validate_json(
        """
        {"goal":"g","nodes":[{"id":"a","description":"x"},{"id":"b","description":"y"}],
         "edges":[{"src":"a","dst":"b"}]}
        """
    )
    planner = ScriptedPlanner([cyclic, good])
    engine = PlanEngine(planner, ScriptedExecutor({}), sleep=_no_sleep())
    report = await engine.run("g")
    assert report.completed
    assert len(planner.feedbacks) == 2
    # 第二次规划拿到了体检反馈
    assert "E_CYCLE" in planner.feedbacks[1]


async def test_planning_fails_after_exhausting_fixes():
    cyclic = PlanGraph.model_validate_json(
        """
        {"goal":"g","nodes":[{"id":"a","description":"x"}],
         "edges":[{"src":"a","dst":"a"}]}
        """
    )
    engine = PlanEngine(
        ScriptedPlanner([cyclic, cyclic, cyclic]),
        ScriptedExecutor({}),
        max_plan_fix=1,
        sleep=_no_sleep(),
    )
    with pytest.raises(PlanningError):
        await engine.run("g")


# --------------------------------------------------------------------------- 模板渲染


async def test_upstream_data_is_rendered_into_description():
    """上游的结构化产出通过 {{s1.file_ids}} 注入下游描述。"""
    scripted = ScriptedExecutor(
        {
            "s1": [ok("找到 3 个 jpg", file_ids="f1,f2,f3")],
            "s2": [ok("目录已建", dir_id="d-1")],
        }
    )
    engine = PlanEngine(ScriptedPlanner([base_plan()]), scripted, sleep=_no_sleep())
    await engine.run("备份照片并分享")
    assert scripted.descriptions["s3"] == "移动 f1,f2,f3 到 d-1"


def test_missing_placeholder_is_preserved_not_silently_emptied():
    """上游没给值时保留占位符，避免工具参数被静默置空。"""
    from app.planning import NodeOutcome

    results = {"s1": NodeOutcome(node_id="s1", status="ok", data={})}
    rendered = render_description("移动 {{s1.file_ids}}", results)
    assert rendered == "移动 {{s1.file_ids}}"
