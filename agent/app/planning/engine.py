"""DAG 计划编排引擎：体检 → 分层调度 → 三层容错恢复。

一次运行的完整链路：

    用户目标
      └─ Planner 输出结构化 JSON（nodes + edges）   ← 强制结构化，不接受纯文本计划
          └─ normalize_plan()   把 depends_on 并进权威边集
             └─ validate_plan() 静态体检（环 / 可达性 / 连接一致性）
                ├─ 不通过 → 把体检报告作为反馈让 Planner 重规划（≤ max_plan_fix 次）
                └─ 通过   → topo_layers() 分层
                            └─ 逐层 asyncio.gather 并行，层间同步屏障
                               每个节点执行失败时按三级容错恢复：
                               Layer1 重试 → Layer2 重规划 → Layer3 再分解
                               用尽则标记 failed，下游依据 AND/OR join 判定 skip
                               （局部失败不阻塞整体工作流）

关于不阻塞：这里刻意区分 failed 与 skipped。某个前置节点彻底失败时，
依赖它的节点会被 skip，但**兄弟分支照常执行**，最终由上层汇总时把未完成
的部分如实告诉用户，而不是整条链路"全有或全无"。
"""
from __future__ import annotations

import asyncio
import re
from collections.abc import Awaitable, Callable, Mapping
from dataclasses import dataclass, field
from typing import Any, Literal, Protocol

from app.planning.schema import (
    ExecResult,
    NodeOutcome,
    PlanGraph,
    PlanNode,
    normalize_plan,
)
from app.planning.scheduler import build_adjacency, index_nodes, topo_layers
from app.planning.validate import InvalidPlanError, ValidationReport, validate_plan


class PlanningError(Exception):
    """Planner 未给出可用计划（无输出 / 反复修正后仍不合法）。"""


class Planner(Protocol):
    """规划能力：顶层拆分、单节点重规划、瓶颈节点再分解。"""

    async def plan(self, goal: str, feedback: str = "") -> PlanGraph | None:
        """顶层规划。`feedback` 非空表示这是带着体检结论的重新规划。"""

    async def replan_node(self, node: PlanNode, error: str) -> PlanNode | None:
        """Layer2：换一种说法/换一条路重新表述同一个子任务（目标不变）。"""

    async def decompose(self, node: PlanNode, error: str) -> PlanGraph | None:
        """Layer3：把该节点再拆成更细粒度的子图，用于绕过障碍。"""


@dataclass(frozen=True)
class ExecContext:
    """交给 Worker 的上下文：渲染后的描述 + 恢复提示 + 上游结果快照。"""

    description: str
    hint: str = ""
    attempt: int = 0
    results: Mapping[str, NodeOutcome] = field(default_factory=dict)


# Worker 形态：(节点, 上下文) -> 执行结果
ExecFn = Callable[[PlanNode, ExecContext], Awaitable[ExecResult]]
# 过程观察者：(事件名, 载荷) —— 供 SSE/日志/终端演示订阅
Observer = Callable[[str, dict[str, Any]], None]


@dataclass(frozen=True)
class RecoveryPolicy:
    """三层恢复协议的参数。"""

    max_retries: int = 2           # Layer1 重试次数（仅对瞬时错误）
    replan_attempts: int = 1       # Layer2 重规划次数
    decompose_attempts: int = 1    # Layer3 再分解次数
    decompose_depth: int = 1       # 允许递归再分解的最大深度
    backoff_base: float = 0.05     # 重试退避基数（秒）


@dataclass
class RunReport:
    """一次运行的最终产物。"""

    goal: str
    plan: PlanGraph
    layers: list[list[str]]
    outcomes: dict[str, NodeOutcome]

    @property
    def succeeded(self) -> list[str]:
        return [k for k, v in self.outcomes.items() if v.status == "ok"]

    @property
    def failed(self) -> list[str]:
        return [k for k, v in self.outcomes.items() if v.status == "failed"]

    @property
    def skipped(self) -> list[str]:
        return [k for k, v in self.outcomes.items() if v.status == "skipped"]

    @property
    def completed(self) -> bool:
        return not self.failed

    def render_summary(self) -> str:
        """给用户看的执行摘要（区分成功/失败/跳过，不掩盖局部失败）。"""
        lines = [f"目标：{self.goal}", f"执行层：{[' + '.join(l) for l in self.layers]}"]
        for out in self.outcomes.values():
            lines.append(_render_outcome(out, indent="  "))
        lines.append(
            f"汇总：成功 {len(self.succeeded)} / 失败 {len(self.failed)} / 跳过 {len(self.skipped)}"
        )
        return "\n".join(lines)


def _render_outcome(out: NodeOutcome, indent: str) -> str:
    """渲染单个节点；Layer3 拆出的子步骤缩进追加在父节点之下。"""
    mark = {"ok": "成功", "failed": "失败", "skipped": "跳过"}.get(out.status, out.status)
    extra = f"（{out.stage}×{out.attempts}）" if out.stage != "none" else ""
    text = out.output or out.error or out.note or ""
    head = f"{indent}[{mark}] {out.node_id}{extra}：{text}"
    for child in out.children.values():
        head += "\n" + _render_outcome(child, indent + "  ")
    return head


# {{s1.file_ids}} → 上游节点的结构化产出
_TEMPLATE_RE = re.compile(r"\{\{\s*([A-Za-z0-9_\-]+)\.([A-Za-z0-9_\-]+)\s*\}\}")


def render_description(text: str, results: Mapping[str, NodeOutcome]) -> str:
    """用已完成节点的 data 渲染描述中的占位符。

    占位符缺失时**原样保留**：让 Worker 看见"这里没拿到值"，
    好过静默替换成空字符串导致工具参数为空的隐性错误。
    """

    def _sub(match: re.Match[str]) -> str:
        node_id, key = match.group(1), match.group(2)
        outcome = results.get(node_id)
        if outcome is None or key not in outcome.data:
            return match.group(0)
        return str(outcome.data[key])

    return _TEMPLATE_RE.sub(_sub, text)


def _emit(observer: Observer | None, event: str, **payload: Any) -> None:
    if observer is not None:
        observer(event, payload)


class PlanEngine:
    """体检 + 分层调度 + 分层恢复的编排入口。"""

    def __init__(
        self,
        planner: Planner,
        executor: ExecFn,
        *,
        policy: RecoveryPolicy | None = None,
        max_plan_fix: int = 2,
        observer: Observer | None = None,
        sleep: Callable[[float], Awaitable[None]] | None = None,
    ) -> None:
        self.planner = planner
        self.executor = executor
        self.policy = policy or RecoveryPolicy()
        self.max_plan_fix = max_plan_fix
        self.observer = observer
        self._sleep = sleep or asyncio.sleep

    # ---------------------------------------------------------------- 规划

    async def plan_and_validate(self, goal: str) -> tuple[PlanGraph, ValidationReport | None]:
        """规划并强制体检；不合法则带反馈重规划，超出次数仍未过则抛错。"""
        feedback = ""
        last_report: ValidationReport | None = None
        for turn in range(self.max_plan_fix + 1):
            raw = await self.planner.plan(goal, feedback)
            if raw is None:
                raise PlanningError("Planner 未给出计划")
            plan = normalize_plan(raw)
            report = validate_plan(plan)
            last_report = report
            _emit(
                self.observer,
                "validated",
                turn=turn,
                ok=report.ok,
                warnings=len(report.warnings),
                codes=[i.code for i in report.issues],
            )
            if report.ok:
                if report.warnings:
                    _emit(
                        self.observer,
                        "validated_warnings",
                        detail=report.render_feedback(),
                    )
                return plan, report
            feedback = report.render_feedback()
            _emit(self.observer, "replan_plan", turn=turn, detail=feedback)
        assert last_report is not None
        raise PlanningError(f"计划反复修正后仍未通过体检：{last_report.render_feedback()}")

    # ---------------------------------------------------------------- 运行

    async def run(self, goal: str) -> RunReport:
        plan, _ = await self.plan_and_validate(goal)
        layers = topo_layers(plan)
        _emit(self.observer, "planned", goal=goal, layers=layers)
        outcomes = await self._run_plan(plan, {}, depth=0)
        return RunReport(goal=goal, plan=plan, layers=layers, outcomes=outcomes)

    async def _run_plan(
        self, plan: PlanGraph, inherited: dict[str, NodeOutcome], depth: int
    ) -> dict[str, NodeOutcome]:
        """按层执行一张图；`inherited` 用于递归子图继承父图已完成的结果。"""
        results: dict[str, NodeOutcome] = dict(inherited)
        nodes = index_nodes(plan)
        layers = topo_layers(plan)
        for level, layer in enumerate(layers):
            _emit(self.observer, "layer_start", level=level, nodes=layer)
            gates = {nid: self._gate(nid, plan, results) for nid in layer}
            tasks = {
                nid: asyncio.create_task(
                    self._dispatch(nodes[nid], plan, results, depth, gates[nid])
                )
                for nid, gate in gates.items()
                if gate[0]
            }
            for nid, (runnable, reason) in gates.items():
                if not runnable:
                    results[nid] = NodeOutcome(
                        node_id=nid, status="skipped", note=reason
                    )
                    _emit(self.observer, "skipped", node_id=nid, reason=reason)
            if tasks:
                done = await asyncio.gather(*tasks.values())
                for nid, outcome in zip(tasks.keys(), done):
                    results[nid] = outcome
        return results

    def _gate(
        self, node_id: str, plan: PlanGraph, results: dict[str, NodeOutcome]
    ) -> tuple[bool, str]:
        """依赖放行判定（AND/OR join）；不放行的节点标 skipped 而非报错。"""
        adj = build_adjacency(plan)
        node = index_nodes(plan)[node_id]
        preds = adj.pred.get(node_id, [])
        if not preds:
            return True, ""
        missing = [p for p in preds if p not in results]
        if missing:
            return False, f"前驱未完成：{'、'.join(missing)}"
        ok_map = {p: results[p].status == "ok" for p in preds}
        if node.join_policy == "all":
            not_ok = [p for p, is_ok in ok_map.items() if not is_ok]
            return (not not_ok), (f"AND join 未满足，前驱失败：{'、'.join(not_ok)}" if not_ok else "")
        if any(ok_map.values()):
            return True, ""
        return (
            False,
            f"OR join 未满足，前驱全部失败/跳过：{'、'.join(list(ok_map))}",
        )

    async def _dispatch(
        self,
        node: PlanNode,
        plan: PlanGraph,
        results: dict[str, NodeOutcome],
        depth: int,
        gate: tuple[bool, str],
    ) -> NodeOutcome:
        runnable, reason = gate
        if not runnable:
            return NodeOutcome(node_id=node.id, status="skipped", note=reason)
        _emit(self.observer, "node_start", node_id=node.id, group=node.worker_group)
        outcome = await self._execute_with_recovery(node, results, depth)
        _emit(
            self.observer,
            "node_result",
            node_id=node.id,
            status=outcome.status,
            stage=outcome.stage,
            attempts=outcome.attempts,
        )
        return outcome

    # ---------------------------------------------------------- 三层容错恢复

    async def _execute_with_recovery(
        self, node: PlanNode, results: dict[str, NodeOutcome], depth: int
    ) -> NodeOutcome:
        """Layer1 重试 → Layer2 重规划 → Layer3 再分解，逐级升级而不直接放弃。"""
        policy = self.policy
        attempts = 0
        error = ""

        async def _attempt_once(current: PlanNode, hint: str) -> ExecResult:
            nonlocal attempts
            attempts += 1
            description = render_description(current.description, results)
            ctx = ExecContext(
                description=description, hint=hint, attempt=attempts, results=dict(results)
            )
            return await self.executor(current, ctx)

        # ---- Layer 1：重试（只对瞬时错误有意义）
        for retry in range(policy.max_retries + 1):
            result = await _attempt_once(node, "")
            if result.ok:
                return NodeOutcome(
                    node_id=node.id,
                    status="ok",
                    output=result.output,
                    data=result.data,
                    attempts=attempts,
                    stage="retry" if retry else "none",
                )
            error = result.error or "未知错误"
            if not result.transient:
                break  # 确定性失败，重试无用，直接升级
            if retry < policy.max_retries:
                delay = policy.backoff_base * (2**retry)
                _emit(self.observer, "retry", node_id=node.id, retry=retry + 1, delay=delay)
                await self._sleep(delay)

        # ---- Layer 2：重规划（换表述/换路径，目标不变）
        for turn in range(policy.replan_attempts):
            alt = await self.planner.replan_node(node, error)
            if alt is None:
                break
            alt = alt.model_copy(update={"id": node.id})
            _emit(self.observer, "replan", node_id=node.id, turn=turn + 1, description=alt.description)
            result = await _attempt_once(alt, "上一次失败已换了新的执行思路，请按新描述执行")
            if result.ok:
                return NodeOutcome(
                    node_id=node.id,
                    status="ok",
                    output=result.output,
                    data=result.data,
                    attempts=attempts,
                    stage="replan",
                )
            error = result.error or error

        # ---- Layer 3：再分解（把瓶颈节点拆细，绕过障碍；失败也不阻塞整体）
        children: dict[str, NodeOutcome] = {}
        if depth < policy.decompose_depth:
            for turn in range(policy.decompose_attempts):
                sub = await self.planner.decompose(node, error)
                if sub is None:
                    break
                sub = normalize_plan(sub)
                report = validate_plan(sub)
                if not report.ok:
                    _emit(
                        self.observer,
                        "decompose_invalid",
                        node_id=node.id,
                        detail=report.render_feedback(),
                    )
                    continue
                sub = _prefix_subgraph(sub, node.id)
                _emit(
                    self.observer,
                    "decompose",
                    node_id=node.id,
                    turn=turn + 1,
                    sub_nodes=[n.id for n in sub.nodes],
                )
                sub_results = await self._run_plan(sub, results, depth + 1)
                prefix = f"{node.id}::"
                children = {k: v for k, v in sub_results.items() if k.startswith(prefix)}
                if children and all(o.status == "ok" for o in children.values()):
                    merged: dict[str, Any] = {}
                    for outcome in children.values():
                        merged.update(outcome.data)
                    return NodeOutcome(
                        node_id=node.id,
                        status="ok",
                        output=f"已通过再分解的 {len(children)} 个细粒度步骤完成",
                        data=merged,
                        attempts=attempts,
                        stage="decompose",
                        children=children,
                    )
                error = "再分解后的细粒度步骤仍有失败"

        return NodeOutcome(
            node_id=node.id,
            status="failed",
            error=error,
            attempts=attempts,
            stage="exhausted",
            children=children,
        )


def _prefix_subgraph(sub: PlanGraph, parent_id: str) -> PlanGraph:
    """给子图节点加父节点前缀，避免递归时 id 冲突污染全局结果。"""
    prefix = f"{parent_id}::"
    mapping = {n.id: f"{prefix}{n.id}" for n in sub.nodes}
    nodes = [n.model_copy(update={"id": mapping[n.id]}) for n in sub.nodes]
    edges = [
        e.model_copy(update={"src": mapping.get(e.src, e.src), "dst": mapping.get(e.dst, e.dst)})
        for e in sub.edges
    ]
    return PlanGraph(goal=sub.goal, nodes=nodes, edges=edges)


# 供类型标注复用
Stage = Literal["none", "retry", "replan", "decompose", "exhausted"]
