"""工具绑定回归测试：执行侧模型必须挂上 MCP 工具 schema，否则 tool_calls 恒空。

历史上 build_agent_graph 直接把原始模型交给 react/worker，模型从未见过工具
schema，生产上工具一次都不会被调用（测试用手工构造 tool_calls 的替身掩盖了
这一点）。此处用可绑定工具的替身锁死该行为。
"""
import pytest
from langchain_core.messages import AIMessage, HumanMessage

from app.core.checkpoint import build_checkpointer
from app.core.graph import build_agent_graph
from app.core.nodes.intent import IntentDecision
from app.core.nodes.plan import Plan, PlanStep
from app.mcp.client import McpToolInfo
from tests.helpers import BindableScriptedModel, StubStructured

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
    async def connect(self):
        pass

    async def close(self):
        pass

    async def list_tools(self):
        return MCP_TOOLS

    async def call_tool(self, name, arguments, user_id):
        return "搜索结果"


def _cp():
    return build_checkpointer(force_memory=True)


@pytest.mark.asyncio
async def test_react_agent_uses_tool_bound_model():
    """react_agent 使用的模型必须绑定了 MCP 工具。"""
    model = BindableScriptedModel([AIMessage(content="已找到合同")])
    graph = await build_agent_graph(
        model=model, mcp=FakeMCP(), user_id="42", checkpointer=_cp()
    )
    out = await graph.ainvoke(
        {"messages": [HumanMessage(content="帮我找合同")], "user_input": "帮我找合同"},
        {"configurable": {"thread_id": "b1"}},
    )
    assert model.bound is not None
    assert model.bound.tool_names == ["search_files"]
    assert out["final_answer"] == "已找到合同"
    # 调用落在绑定后的替身上，未绑定模型不应被调用
    assert len(model.bound.calls) == 1
    assert model.calls == []


@pytest.mark.asyncio
async def test_chitchat_uses_unbound_model():
    """直答分支使用未绑定工具的原始模型。"""
    model = BindableScriptedModel([AIMessage(content="我是你的云盘助手")])
    intent_model = StubStructured(
        [IntentDecision(intent="chitchat", confidence=0.99, mode="react")]
    )
    graph = await build_agent_graph(
        model=model,
        mcp=FakeMCP(),
        user_id="42",
        checkpointer=_cp(),
        intent_model=intent_model,
    )
    out = await graph.ainvoke(
        {"messages": [HumanMessage(content="你是谁")], "user_input": "你是谁"},
        {"configurable": {"thread_id": "b2"}},
    )
    assert out["mode"] == "chitchat"
    assert out["final_answer"] == "我是你的云盘助手"
    assert model.bound is not None  # 执行侧照常绑定
    assert len(model.calls) == 1  # 直答走未绑定模型
    assert model.bound.calls == []


@pytest.mark.asyncio
async def test_worker_defaults_to_bound_model():
    """plan 路径未显式注入 worker_model 时，worker 也要用绑定工具的模型。"""
    model = BindableScriptedModel([AIMessage(content="子任务已完成")])
    intent_model = StubStructured(
        [IntentDecision(intent="search", confidence=0.9, mode="plan")]
    )
    planner_model = StubStructured(
        [
            Plan(
                steps=[
                    PlanStep(
                        task_id="s1", description="搜索合同文件", worker_group="search"
                    )
                ]
            )
        ]
    )
    graph = await build_agent_graph(
        model=model,
        mcp=FakeMCP(),
        user_id="42",
        checkpointer=_cp(),
        intent_model=intent_model,
        planner_model=planner_model,
    )
    out = await graph.ainvoke(
        {"messages": [HumanMessage(content="帮我搜合同")], "user_input": "帮我搜合同"},
        {"configurable": {"thread_id": "b3"}},
    )
    assert model.bound is not None
    assert model.bound.calls, "worker 必须使用绑定工具的模型"
    assert [r["step_id"] for r in out["results"]] == ["s1"]
