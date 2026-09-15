import asyncio

import pytest
from langchain_core.messages import AIMessage, HumanMessage

from app.core.checkpoint import build_checkpointer
from app.core.graph import build_agent_graph
from app.core.nodes.intent import IntentDecision
from app.core.nodes.plan import Plan, PlanStep
from app.mcp.client import McpToolInfo
from tests.helpers import ScriptedModel, StubStructured

MCP_TOOLS = [
    McpToolInfo(
        name="search_files",
        description="搜索文件",
        input_schema={
            "type": "object",
            "properties": {"keyword": {"type": "string"}},
            "required": ["keyword"],
        },
    )
]


class FakeMCP:
    def __init__(self):
        self.called: list[tuple[str, dict]] = []

    async def list_tools(self):
        return MCP_TOOLS

    async def call_tool(self, name, arguments, user_id):
        self.called.append((name, arguments))
        return "搜索完成"


def _thread(n: str) -> dict:
    return {"configurable": {"thread_id": n}}


def _plan_steps():
    return [
        PlanStep(task_id="s1", description="创建文件夹 agent", worker_group="file_ops"),
        PlanStep(task_id="s2", description="搜索 合同 文件", worker_group="search"),
    ]


@pytest.mark.asyncio
async def test_plan_path_runs_workers_and_summarize():
    """router=plan → planner 拆两子任务 → 并行 worker 各出结果 → collector/summarizer。"""
    intent_model = StubStructured(
        [
            IntentDecision(
                intent="file_ops", confidence=0.93, mode="plan", reason="多操作请求"
            )
        ]
    )
    planner_model = StubStructured([Plan(steps=_plan_steps())])
    worker_model = ScriptedModel(
        [AIMessage(content="已创建文件夹 agent"), AIMessage(content="找到 1 份合同")]
    )
    summarizer_model = ScriptedModel(
        [AIMessage(content="已全部完成：新建文件夹 agent，并找到 1 份合同文件。")]
    )
    mcp = FakeMCP()
    graph = await build_agent_graph(
        model=worker_model,
        mcp=mcp,
        user_id="42",
        checkpointer=build_checkpointer(force_memory=True),
        intent_model=intent_model,
        planner_model=planner_model,
        worker_model=worker_model,
        summarizer_model=summarizer_model,
    )
    out = await graph.ainvoke(
        {
            "messages": [HumanMessage(content="帮我新建一个叫 agent 的文件夹，再搜一下有没有合同文件")],
            "user_input": "帮我新建一个叫 agent 的文件夹，再搜一下有没有合同文件",
        },
        _thread("p1"),
    )
    assert out["mode"] == "plan"
    assert sorted(r["step_id"] for r in out["results"]) == ["s1", "s2"]
    assert all(r["status"] == "ok" for r in out["results"])
    assert out["final_answer"].startswith("已全部完成")


class SlowWorkerModel:
    """统计并发深度的 worker 替身：两个 Send 分支应重叠执行。"""

    def __init__(self, delay: float = 0.05):
        self.active = 0
        self.peak = 0
        self.delay = delay

    async def ainvoke(self, messages):
        self.active += 1
        self.peak = max(self.peak, self.active)
        await asyncio.sleep(self.delay)
        self.active -= 1
        return AIMessage(content="子任务已完成")


@pytest.mark.asyncio
async def test_workers_run_in_parallel_via_send():
    """Send 扇出后两个 worker 并行执行（peak 并发 ≥ 2）。"""
    intent_model = StubStructured(
        [IntentDecision(intent="file_ops", confidence=0.9, mode="plan", reason="multi")]
    )
    planner_model = StubStructured([Plan(steps=_plan_steps())])
    slow = SlowWorkerModel()
    summarizer_model = ScriptedModel([AIMessage(content="全部完成")])
    mcp = FakeMCP()
    graph = await build_agent_graph(
        model=slow,
        mcp=mcp,
        user_id="42",
        checkpointer=build_checkpointer(force_memory=True),
        intent_model=intent_model,
        planner_model=planner_model,
        worker_model=slow,
        summarizer_model=summarizer_model,
    )
    out = await graph.ainvoke(
        {
            "messages": [HumanMessage(content="批量操作")],
            "user_input": "批量操作",
        },
        _thread("p2"),
    )
    assert slow.peak >= 2
    assert len(out["results"]) == 2
