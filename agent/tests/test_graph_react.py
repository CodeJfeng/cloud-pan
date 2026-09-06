import pytest
from langchain_core.messages import AIMessage, HumanMessage

from app.core.checkpoint import build_checkpointer
from app.core.graph import build_agent_graph
from app.mcp.client import McpToolInfo
from tests.helpers import ScriptedModel

MCP_TOOLS = [
    McpToolInfo(
        name="search_files",
        description="搜索文件",
        input_schema={
            "type": "object",
            "properties": {
                "userId": {"type": "string"},
                "keyword": {"type": "string"},
            },
            "required": ["userId", "keyword"],
        },
    )
]


class FakeMCP:
    """与 MCPClient 同接口的最小替身。"""

    def __init__(self):
        self.calls = []

    async def connect(self):
        pass

    async def close(self):
        pass

    async def list_tools(self):
        return MCP_TOOLS

    async def call_tool(self, name, arguments, user_id):
        self.calls.append((name, arguments, user_id))
        return f"{name} 结果: keyword={arguments.get('keyword')}"


def _cp():
    return build_checkpointer(force_memory=True)


@pytest.mark.asyncio
async def test_react_simple_flow_no_tool():
    model = ScriptedModel([AIMessage(content="你好呀")])
    graph = await build_agent_graph(
        model=model, mcp=FakeMCP(), user_id="42", checkpointer=_cp()
    )
    out = await graph.ainvoke(
        {"messages": [HumanMessage(content="你好")], "user_input": "你好"},
        {"configurable": {"thread_id": "t1"}},
    )
    assert out["final_answer"] == "你好呀"


@pytest.mark.asyncio
async def test_react_tool_loop_then_final():
    """LLM → 工具 → LLM 收尾：整图可跑通，工具调用注入 userId、schema 已剥离。"""
    model = ScriptedModel(
        [
            AIMessage(
                content="",
                tool_calls=[
                    {"name": "search_files", "args": {"keyword": "简历"}, "id": "call_1"}
                ],
            ),
            AIMessage(content="已找到 1 个文件。"),
        ]
    )
    mcp = FakeMCP()
    graph = await build_agent_graph(
        model=model, mcp=mcp, user_id="42", checkpointer=_cp()
    )
    out = await graph.ainvoke(
        {"messages": [HumanMessage(content="帮我找简历")], "user_input": "帮我找简历"},
        {"configurable": {"thread_id": "t2"}},
    )
    assert out["final_answer"] == "已找到 1 个文件。"
    # 工具被调用且注入 userId（schema 中不含 userId 由 McpToolFactory 单测覆盖）
    assert mcp.calls == [("search_files", {"keyword": "简历"}, "42")]
