import pytest
from langchain_core.messages import AIMessage, HumanMessage, ToolMessage
from langchain_core.tools import tool

from app.core.nodes import react
from tests.helpers import FakeWriter, ScriptedModel


@tool
def fake_search(keyword: str) -> str:
    """搜索文件（非敏感）。"""
    return f"找到: {keyword}"


@tool
def fake_delete(fileIds: list[str]) -> str:
    """删除文件（敏感）。"""
    return f"已删除: {fileIds}"


@tool
def boom_list(parentId: str) -> str:
    """总是抛错的工具，验证异常回填。"""
    raise RuntimeError("boom")


def _deps(model=None, tools=None, confirm=None, sensitive=None):
    return react.ReactDeps(
        model=model,
        tools_by_name=tools or {},
        confirm=confirm,
        sensitive=frozenset(sensitive or []),
    )


@pytest.fixture
def writer(monkeypatch):
    w = FakeWriter()
    monkeypatch.setattr("app.core.events.get_stream_writer", lambda: w)
    return w


@pytest.mark.asyncio
async def test_react_agent_emits_llm_decision_and_routes_to_tools(writer):
    ai = AIMessage(
        content="",
        tool_calls=[{"name": "fake_search", "args": {"keyword": "简历"}, "id": "call_1"}],
    )
    deps = _deps(model=ScriptedModel([ai]))
    state = {"messages": [HumanMessage(content="帮我搜简历")], "retries": 0}
    out = await react.react_agent(state, deps)
    assert out["mode"] == "react"
    assert react.react_route({**state, **out}) == "tools"
    decision = next(e for e in writer.events if e["type"] == "llm_decision")
    assert decision["tools"] == ["fake_search"]
    assert decision["duration_ms"] >= 0


@pytest.mark.asyncio
async def test_react_agent_final_answer_when_no_tool(writer):
    deps = _deps(model=ScriptedModel([AIMessage(content="找到了文件")]))
    state = {"messages": [HumanMessage(content="找简历")], "retries": 0}
    out = await react.react_agent(state, deps)
    assert out["final_answer"] == "找到了文件"
    assert "mode" not in out
    assert react.react_route({**state, **out}) == "end"


@pytest.mark.asyncio
async def test_react_agent_nudges_promised_search(writer):
    """命中承诺搜索句式且未超限：注入强制搜索的用户消息并回到 agent 循环。"""
    ai = AIMessage(content="没有找到，让我再用关键词搜一下")
    deps = _deps(model=ScriptedModel([ai]))
    state = {"messages": [HumanMessage(content="找文件")], "retries": 0}
    out = await react.react_agent(state, deps)
    assert out["retries"] == 1
    assert any(e.get("type") == "llm_decision" and e.get("nudge") for e in writer.events)
    forced = [m for m in out["messages"] if isinstance(m, HumanMessage) and "直接调用搜索工具" in m.content]
    assert forced
    assert react.react_route({**state, **out}) == "agent"


@pytest.mark.asyncio
async def test_react_agent_no_nudge_when_retries_exhausted(writer):
    """轮次超限后不再 nudge，直接收尾为最终答案。"""
    ai = AIMessage(content="让我再搜一下")
    deps = _deps(model=ScriptedModel([ai]))
    state = {"messages": [HumanMessage(content="找文件")], "retries": 2}
    out = await react.react_agent(state, deps)
    assert out["final_answer"] == "让我再搜一下"
    assert not any(e.get("nudge") for e in writer.events)


@pytest.mark.asyncio
async def test_tool_error_is_fed_back(writer):
    ai = AIMessage(
        content="",
        tool_calls=[{"name": "boom_list", "args": {"parentId": "p1"}, "id": "call_1"}],
    )
    state = {"messages": [HumanMessage(content="查文件"), ai]}
    deps = _deps(tools={"boom_list": boom_list})
    out = await react.react_tools(state, deps)
    tm = out["messages"][-1]
    assert isinstance(tm, ToolMessage)
    assert "boom" in tm.content and "工具执行异常" in tm.content
    te = next(e for e in writer.events if e["type"] == "tool_end")
    assert te["tool"] == "boom_list"
    assert te["error"] is True
    assert "工具执行异常" in te["result"]


@pytest.mark.asyncio
async def test_sensitive_tool_rejected_skips_execution(writer):
    confirm_calls = []
    async def confirm(tool_name, arguments, request_id):
        confirm_calls.append((tool_name, arguments, request_id))
        return False

    ai = AIMessage(
        content="",
        tool_calls=[{"name": "fake_delete", "args": {"fileIds": ["f1"]}, "id": "call_1"}],
    )
    state = {"messages": [HumanMessage(content="删掉 f1"), ai]}
    deps = _deps(tools={"fake_delete": fake_delete}, confirm=confirm, sensitive={"fake_delete"})
    out = await react.react_tools(state, deps)
    assert confirm_calls and confirm_calls[0][0] == "fake_delete"
    req = next(e for e in writer.events if e["type"] == "confirmation_required")
    assert req["requestId"] == confirm_calls[0][2]
    assert not any(e["type"] == "tool_start" for e in writer.events)
    tm = out["messages"][-1]
    assert "用户拒绝" in tm.content
    skipped = next(e for e in writer.events if e["type"] == "tool_end")
    assert skipped.get("skipped") is True


@pytest.mark.asyncio
async def test_sensitive_tool_approved_executes(writer):
    async def confirm(tool_name, arguments, request_id):
        return True

    ai = AIMessage(
        content="",
        tool_calls=[{"name": "fake_delete", "args": {"fileIds": ["f1"]}, "id": "call_2"}],
    )
    state = {"messages": [HumanMessage(content="删掉 f1"), ai]}
    deps = _deps(tools={"fake_delete": fake_delete}, confirm=confirm, sensitive={"fake_delete"})
    await react.react_tools(state, deps)
    assert any(e["type"] == "tool_start" for e in writer.events)
    te = next(e for e in writer.events if e["type"] == "tool_end")
    assert te.get("skipped") is None
    assert "已删除" in te["result"]
