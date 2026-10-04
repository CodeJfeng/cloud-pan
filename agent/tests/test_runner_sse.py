import pytest
from langchain_core.messages import AIMessage, HumanMessage

from app.core import runner
from app.core.checkpoint import build_checkpointer
from app.core.graph import build_agent_graph
from app.core.runner import get_confirmation, run_agent_stream
from app.mcp.client import McpToolInfo
from tests.helpers import ScriptedModel

MCP_TOOLS = [
    McpToolInfo(
        name="delete_file",
        description="删除文件（敏感）",
        input_schema={
            "type": "object",
            "properties": {
                "fileIds": {"type": "array", "items": {"type": "string"}},
            },
            "required": ["fileIds"],
        },
    ),
    McpToolInfo(
        name="search_files",
        description="搜索文件",
        input_schema={
            "type": "object",
            "properties": {"keyword": {"type": "string"}},
            "required": ["keyword"],
        },
    ),
]


class FakeMCP:
    def __init__(self):
        self.called: list[tuple[str, dict]] = []

    async def list_tools(self):
        return MCP_TOOLS

    async def call_tool(self, name, arguments, user_id):
        self.called.append((name, arguments))
        return "已删除 1 个文件" if name == "delete_file" else "搜索完成"


class IndexedFakeMCP(FakeMCP):
    """search_files 返回带 fileId/fileName 的 JSON，用于验证审批可读化。"""

    async def call_tool(self, name, arguments, user_id):
        self.called.append((name, arguments))
        if name == "search_files":
            return (
                '[search_files] 云盘响应: {"code":0,"data":['
                '{"fileId":"s31d-resume","parentId":"Hkpu-agent",'
                '"parentFilename":"agent","fileName":"李京峰-个人简历.pdf","folderFlag":0}]}'
            )
        return "已删除 1 个文件"


def ai_tool(name, args, call_id):
    return AIMessage(
        content="", tool_calls=[{"name": name, "args": args, "id": call_id}]
    )


def _thread(n: str) -> dict:
    return {"configurable": {"thread_id": n}}


async def _drain(graph, mcp, thread_id, inputs, on_confirm=None):
    """消费整条 SSE 事件流；on_confirm(request_id) 返回批复或 None（超时）。"""
    events = []
    async for ev in run_agent_stream(
        graph=graph,
        inputs=inputs,
        config=_thread(thread_id),
        user_id="42",
    ):
        events.append(ev)
        if ev["type"] == "confirmation_required" and on_confirm is not None:
            entry = get_confirmation(ev["requestId"])
            assert entry is not None, "confirmation_required 后注册表应有对应 future"
            _, fut = entry
            fut.set_result(on_confirm(ev["requestId"]))
    return events


@pytest.mark.asyncio
async def test_runner_interrupt_then_resume_approved():
    """敏感工具触发 interrupt → confirmation_required → 批复 True → 工具执行 → done。"""
    model = ScriptedModel(
        [
            ai_tool("delete_file", {"fileIds": ["f1"]}, "call_1"),
            AIMessage(content="已删除完成"),
        ]
    )
    mcp = FakeMCP()
    graph = await build_agent_graph(
        model=model, mcp=mcp, user_id="42",
        checkpointer=build_checkpointer(force_memory=True),
    )
    events = await _drain(
        graph, mcp, "t1",
        {"messages": [HumanMessage(content="删除 f1")], "user_input": "删除 f1"},
        on_confirm=lambda rid: True,
    )
    types = [e["type"] for e in events]
    assert types[0] == "thinking"
    assert "confirmation_required" in types
    assert "tool_start" in types
    assert mcp.called == [("delete_file", {"fileIds": ["f1"]})]
    assert types[-1] == "done"
    assert "已删除完成" in events[-1]["content"]


@pytest.mark.asyncio
async def test_runner_interrupt_timeout_rejected(monkeypatch):
    """确认无人批复（超时视为拒绝）：工具不执行，事件带 skipped。"""
    monkeypatch.setattr(runner, "CONFIRM_TIMEOUT_SECONDS", 0.05)
    model = ScriptedModel(
        [
            ai_tool("delete_file", {"fileIds": ["f1"]}, "call_1"),
            AIMessage(content="已取消删除"),
        ]
    )
    mcp = FakeMCP()
    graph = await build_agent_graph(
        model=model, mcp=mcp, user_id="42",
        checkpointer=build_checkpointer(force_memory=True),
    )
    events = await _drain(
        graph, mcp, "t2",
        {"messages": [HumanMessage(content="删除 f1")], "user_input": "删除 f1"},
        on_confirm=None,  # 无人批复 → 超时
    )
    types = [e["type"] for e in events]
    assert "confirmation_required" in types
    assert "tool_start" not in types
    assert mcp.called == []
    te = next(e for e in events if e["type"] == "tool_end")
    assert te.get("skipped") is True
    assert "用户拒绝" in te["result"]
    assert types[-1] == "done"


@pytest.mark.asyncio
async def test_runner_multiple_interrupts_same_thread():
    """同一 thread 两次敏感操作可依次 interrupt/resume，全部批复后执行完成。"""
    model = ScriptedModel(
        [
            ai_tool("delete_file", {"fileIds": ["f1"]}, "call_1"),
            ai_tool("delete_file", {"fileIds": ["f2"]}, "call_2"),
            AIMessage(content="两处均已删除"),
        ]
    )
    mcp = FakeMCP()
    graph = await build_agent_graph(
        model=model, mcp=mcp, user_id="42",
        checkpointer=build_checkpointer(force_memory=True),
    )
    events = await _drain(
        graph, mcp, "t3",
        {"messages": [HumanMessage(content="删除 f1 和 f2")], "user_input": "删除 f1 和 f2"},
        on_confirm=lambda rid: True,
    )
    types = [e["type"] for e in events]
    assert types.count("confirmation_required") == 2
    assert types.count("tool_start") == 2
    assert mcp.called == [
        ("delete_file", {"fileIds": ["f1"]}),
        ("delete_file", {"fileIds": ["f2"]}),
    ]
    assert types[-1] == "done"


@pytest.mark.asyncio
async def test_confirmation_carries_readable_display():
    """审批事件要带可读描述：加密 fileId 翻译成文件名，供前端直接展示。

    同时锁死：arguments 仍是工具真正使用的原始参数，展示层不改变执行语义。
    """
    model = ScriptedModel(
        [
            ai_tool("search_files", {"keyword": "简历"}, "call_1"),
            ai_tool("delete_file", {"fileIds": ["s31d-resume"]}, "call_2"),
            AIMessage(content="已删除"),
        ]
    )
    mcp = IndexedFakeMCP()
    graph = await build_agent_graph(
        model=model,
        mcp=mcp,
        user_id="42",
        checkpointer=build_checkpointer(force_memory=True),
    )
    events = await _drain(
        graph,
        mcp,
        "t-display",
        {"messages": [HumanMessage(content="删掉简历")], "user_input": "删掉简历"},
        on_confirm=lambda rid: True,
    )
    conf = next(e for e in events if e["type"] == "confirmation_required")
    display = conf.get("display")
    assert display is not None, "审批事件应带可读描述"
    assert "李京峰-个人简历.pdf" in display["summary"]
    assert display["items"][0]["name"] == "李京峰-个人简历.pdf"
    assert display["items"][0]["kind"] == "文件"
    # 原始参数不变：展示层只影响"看得懂"，不影响"怎么执行"
    assert conf["arguments"] == {"fileIds": ["s31d-resume"]}


@pytest.mark.asyncio
async def test_confirmation_without_index_falls_back_to_ids():
    """索引里没有该 fileId 时，如实放进 unresolved，不猜测名称。"""
    model = ScriptedModel(
        [
            ai_tool("delete_file", {"fileIds": ["f-unknown"]}, "call_1"),
            AIMessage(content="已处理"),
        ]
    )
    mcp = FakeMCP()
    graph = await build_agent_graph(
        model=model,
        mcp=mcp,
        user_id="42",
        checkpointer=build_checkpointer(force_memory=True),
    )
    events = await _drain(
        graph,
        mcp,
        "t-fallback",
        {"messages": [HumanMessage(content="删除 f-unknown")], "user_input": "删除 f-unknown"},
        on_confirm=lambda rid: True,
    )
    conf = next(e for e in events if e["type"] == "confirmation_required")
    display = conf["display"]
    assert display["unresolved"] == ["f-unknown"]
    assert display["items"] == []
