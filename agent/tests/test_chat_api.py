import asyncio
import json

import pytest
from httpx import ASGITransport, AsyncClient

from app.api import chat as chat_module
from app.core.agent import Agent
from app.main import app
from app.mcp.client import McpToolInfo

TOOLS = [
    McpToolInfo(
        name="list_files",
        description="列出文件夹内容",
        input_schema={
            "type": "object",
            "properties": {
                "userId": {"type": "string"},
                "parentId": {"type": "string"},
            },
            "required": ["userId", "parentId"],
        },
    ),
    McpToolInfo(
        name="delete_file",
        description="删除文件/文件夹（移入回收站）",
        input_schema={
            "type": "object",
            "properties": {
                "fileIds": {"type": "array", "items": {"type": "string"}},
            },
            "required": ["fileIds"],
        },
    ),
    McpToolInfo(
        name="transfer_files",
        description="移动文件/文件夹到目标目录",
        input_schema={
            "type": "object",
            "properties": {
                "fileIds": {"type": "array", "items": {"type": "string"}},
                "targetParentId": {"type": "string"},
            },
            "required": ["fileIds", "targetParentId"],
        },
    ),
]


class FakeMCP:
    def __init__(self):
        self.called: list[tuple[str, dict]] = []

    async def connect(self):
        pass

    async def close(self):
        pass

    async def list_tools(self):
        return TOOLS

    async def call_tool(self, name, arguments, user_id):
        self.called.append((name, arguments))
        if name == "delete_file":
            return "已删除 1 个文件"
        if name == "transfer_files":
            return "已移动 2 个文件"
        return "list_files 结果: 3 个文件"


class FakeLLM:
    def __init__(self, script):
        self._script = list(script)
        self.last_tool_calls = []

    async def chat(self, messages, tools=None, temperature=0.7):
        step = self._script.pop(0)
        if step.get("tool_calls"):
            self.last_tool_calls = step["tool_calls"]
            return None
        self.last_tool_calls = []
        return step.get("content")

    async def stream(self, messages, tools=None, temperature=0.7):
        for piece in ["你", "好", "！"]:
            yield piece


@pytest.fixture
def fake_deps(monkeypatch):
    from app.api import chat as chat_mod
    from app.storage.session_store import SessionStore

    mcp = FakeMCP()
    llm = FakeLLM([])
    store = SessionStore("sqlite+aiosqlite:///:memory:")
    monkeypatch.setattr(chat_mod, "_store", store)

    def _make(llm_script):
        fake_llm = FakeLLM(llm_script)
        chat_mod._llm = fake_llm
        chat_mod._mcp = mcp
        return fake_llm, mcp

    chat_mod._llm = llm
    chat_mod._mcp = mcp
    return _make, mcp


async def _sse_posts(req_body):
    transport = ASGITransport(app=app)
    async with AsyncClient(transport=transport, base_url="http://test") as c:
        async with c.stream("POST", "/api/agent/chat", json=req_body) as resp:
            assert resp.status_code == 200
            assert resp.headers["content-type"].startswith("text/event-stream")
            body = await resp.aread()
    return body.decode()


def parse_sse(body: str):
    events = []
    for block in body.split("\n\n"):
        if not block.strip():
            continue
        ev, _, data = block.partition("\n")
        assert ev.startswith("event:")
        assert data.startswith("data:")
        events.append((ev.split(":", 1)[1].strip(), json.loads(data.split(":", 1)[1].strip())))
    return events


@pytest.mark.asyncio
async def test_sse_direct_answer(fake_deps):
    _make, _ = fake_deps
    _make([{"content": "直接回答"}])
    body = await _sse_posts({"message": "你好", "userId": "1"})
    events = parse_sse(body)
    types = [t for t, _ in events]
    assert types[-1] == "done"
    # 无工具事件
    assert "tool_start" not in types


@pytest.mark.asyncio
async def test_sse_tool_flow(fake_deps):
    _make, mcp = fake_deps
    _make(
        [
            {
                "tool_calls": [
                    {
                        "id": "call_1",
                        "type": "function",
                        "function": {
                            "name": "list_files",
                            "arguments": json.dumps({"parentId": "abc"}),
                        },
                    }
                ]
            },
            {"content": "好的"},
        ]
    )
    body = await _sse_posts({"message": "看看 abc", "userId": "42"})
    events = parse_sse(body)
    types = [t for t, _ in events]
    assert types[0] == "llm_decision"
    assert "tool_start" in types
    assert "tool_end" in types
    assert types[-1] == "done"
    tool_evt = [d for t, d in events if t == "tool_start"][0]
    assert tool_evt["tool"] == "list_files"
    # 工具参数随事件透传，供链路追踪落库
    assert tool_evt["arguments"] == {"parentId": "abc"}
    tool_end_evt = [d for t, d in events if t == "tool_end"][0]
    assert tool_end_evt["duration_ms"] is not None


@pytest.mark.asyncio
async def test_session_messages_api(fake_deps):
    """历史消息接口：能查到已保存的 user/assistant 消息，且校验归属。"""
    _make, _ = fake_deps
    _make([{"content": "回答"}] * 2)

    # 第一次对话创建会话并存消息
    body = await _sse_posts({"message": "第一条", "userId": "42"})
    events = parse_sse(body)
    done = [d for t, d in events if t == "done"][0]
    sid = done["sessionId"]
    assert sid

    # 查询历史
    transport = ASGITransport(app=app)
    async with AsyncClient(transport=transport, base_url="http://test") as c:
        resp = await c.get(f"/api/agent/session/{sid}/messages", params={"userId": "42"})
        assert resp.status_code == 200
        data = resp.json()["data"]
        assert data["sessionId"] == sid
        roles = [m["role"] for m in data["messages"]]
        assert roles == ["user", "assistant"]
        assert data["messages"][0]["content"] == "第一条"

    # 越权访问被拒绝
    async with AsyncClient(transport=transport, base_url="http://test") as c:
        resp = await c.get(f"/api/agent/session/{sid}/messages", params={"userId": "999"})
        assert resp.status_code == 200
        assert resp.json()["code"] == 403

    # 不存在的会话返回 404
    async with AsyncClient(transport=transport, base_url="http://test") as c:
        resp = await c.get("/api/agent/session/no_such_sid/messages", params={"userId": "42"})
        assert resp.status_code == 200
        assert resp.json()["code"] == 404


@pytest.mark.asyncio
async def test_trace_api(fake_deps):
    """链路追踪：对话产生的 trace 可查询，步骤完整且校验归属。"""
    _make, _ = fake_deps
    _make(
        [
            {
                "tool_calls": [
                    {
                        "id": "call_1",
                        "type": "function",
                        "function": {
                            "name": "list_files",
                            "arguments": json.dumps({"parentId": "abc"}),
                        },
                    }
                ]
            },
            {"content": "好的"},
        ]
    )
    body = await _sse_posts({"message": "看看 abc", "userId": "42"})
    events = parse_sse(body)
    done = [d for t, d in events if t == "done"][0]
    sid = done["sessionId"]
    trace_id = done["traceId"]
    assert sid and trace_id

    transport = ASGITransport(app=app)
    # 链路详情：步骤顺序 message_in → llm_decision → tool_call → answer
    async with AsyncClient(transport=transport, base_url="http://test") as c:
        resp = await c.get(f"/api/agent/trace/{trace_id}", params={"userId": "42"})
        assert resp.status_code == 200
        steps = resp.json()["data"]["steps"]
        types = [s["stepType"] for s in steps]
        # 一次工具决策 + 一次最终回答决策（均为 llm_decision）
        assert types[0] == "message_in"
        assert types[1] == "llm_decision"
        assert types.count("llm_decision") == 2
        assert types.count("tool_call") == 1
        assert types[-1] == "answer"
        tool_step = [s for s in steps if s["stepType"] == "tool_call"][0]
        assert tool_step["stepName"] == "list_files"
        assert tool_step["payload"]["arguments"] == {"parentId": "abc"}
        assert tool_step["payload"]["result"] == "list_files 结果: 3 个文件"
        assert tool_step["status"] == "ok"
        assert tool_step["durationMs"] is not None

    # 会话内链路列表（含用户消息与回答摘要）
    async with AsyncClient(transport=transport, base_url="http://test") as c:
        resp = await c.get(f"/api/agent/session/{sid}/traces", params={"userId": "42"})
        assert resp.status_code == 200
        rows = resp.json()["data"]
        assert len(rows) >= 1
        assert rows[0]["traceId"] == trace_id
        assert rows[0]["userMessage"] == "看看 abc"
        assert rows[0]["answer"] == "好的"
        assert rows[0]["stepCount"] == 5
        assert rows[0]["hasError"] is False

    # 越权访问被拒绝
    async with AsyncClient(transport=transport, base_url="http://test") as c:
        resp = await c.get(f"/api/agent/trace/{trace_id}", params={"userId": "999"})
        assert resp.status_code == 200
        assert resp.json()["code"] == 403

    # 不存在的链路返回 404
    async with AsyncClient(transport=transport, base_url="http://test") as c:
        resp = await c.get("/api/agent/trace/no_such_trace", params={"userId": "42"})
        assert resp.status_code == 200
        assert resp.json()["code"] == 404


async def _sse_stream(req_body):
    """全缓冲消费 SSE 流，返回 [(event, data), ...]。"""
    transport = ASGITransport(app=app)
    async with AsyncClient(transport=transport, base_url="http://test") as c:
        async with c.stream("POST", "/api/agent/chat", json=req_body) as resp:
            assert resp.status_code == 200
            events = []
            name = None
            buf = []
            async for line in resp.aiter_lines():
                if line == "":
                    if name and buf:
                        events.append((name, json.loads("\n".join(buf))))
                    name = None
                    buf = []
                elif line.startswith("event:"):
                    name = line[6:].strip()
                elif line.startswith("data:"):
                    buf.append(line[5:].strip())
            return events


async def _wait_pending(timeout: float = 10.0) -> tuple[str, tuple[str, asyncio.Future]]:
    """轮询确认注册表，等待对话流挂起并注册确认请求。"""
    deadline = asyncio.get_running_loop().time() + timeout
    while not chat_module._pending_confirmations:
        if asyncio.get_running_loop().time() > deadline:
            raise TimeoutError("等待确认请求注册超时")
        await asyncio.sleep(0.01)
    return next(iter(chat_module._pending_confirmations.items()))


def _call_tool(tool_name: str, arguments: dict):
    return {
        "id": "call_1",
        "type": "function",
        "function": {"name": tool_name, "arguments": json.dumps(arguments)},
    }


@pytest.mark.asyncio
async def test_confirm_timeout_rejected(fake_deps, monkeypatch):
    """敏感操作（删除）无人批复（超时视为拒绝）：工具不执行，事件与链路标记 rejected。"""
    monkeypatch.setattr(chat_module, "CONFIRM_TIMEOUT_SECONDS", 0.1)
    _make, mcp = fake_deps
    _make(
        [
            {"tool_calls": [_call_tool("delete_file", {"fileIds": ["f1"]})]},
            {"content": "好的，已取消删除"},
        ]
    )
    events = await _sse_stream({"message": "删除 f1", "userId": "42"})
    types = [t for t, _ in events]
    assert "confirmation_required" in types
    # 未获批准则不会真正执行工具（无 tool_start，FakeMCP 未收到调用）
    assert "tool_start" not in types
    assert mcp.called == []
    tool_end = [d for t, d in events if t == "tool_end"][0]
    assert tool_end["skipped"] is True
    assert types[-1] == "done"

    # 链路追踪：confirmation 步骤被拒绝、tool_call 步骤标记 rejected
    done = [d for t, d in events if t == "done"][0]
    trace_id = done["traceId"]
    transport = ASGITransport(app=app)
    async with AsyncClient(transport=transport, base_url="http://test") as c:
        resp = await c.get(f"/api/agent/trace/{trace_id}", params={"userId": "42"})
        assert resp.status_code == 200
        steps = resp.json()["data"]["steps"]
        confirm_steps = [s for s in steps if s["stepType"] == "confirmation"]
        assert any(s["status"] == "rejected" for s in confirm_steps)
        tool_steps = [s for s in steps if s["stepType"] == "tool_call"]
        assert tool_steps and tool_steps[0]["status"] == "rejected"


@pytest.mark.asyncio
async def test_confirm_approved_flow(fake_deps):
    """敏感操作（移动）获用户批准后正常执行。"""
    _make, mcp = fake_deps
    _make(
        [
            {
                "tool_calls": [
                    _call_tool(
                        "transfer_files",
                        {"fileIds": ["f1", "f2"], "targetParentId": "t1"},
                    )
                ]
            },
            {"content": "已移动完成"},
        ]
    )
    task = asyncio.create_task(_sse_stream({"message": "移动 f1 f2 到 t1", "userId": "42"}))
    request_id, (owner_id, fut) = await _wait_pending()
    assert owner_id == "42"
    fut.set_result(True)  # 模拟用户点击"允许执行"
    events = await task

    types = [t for t, _ in events]
    assert "confirmation_required" in types
    assert "tool_start" in types
    assert mcp.called == [
        ("transfer_files", {"fileIds": ["f1", "f2"], "targetParentId": "t1"})
    ]
    tool_end = [d for t, d in events if t == "tool_end"][0]
    assert "skipped" not in tool_end
    assert types[-1] == "done"


@pytest.mark.asyncio
async def test_confirm_wrong_user_rejected(fake_deps):
    """越权用户不能替他人批准敏感操作。"""
    _make, _ = fake_deps
    _make(
        [
            {"tool_calls": [_call_tool("delete_file", {"fileIds": ["f1"]})]},
            {"content": "已删除"},
        ]
    )
    task = asyncio.create_task(_sse_stream({"message": "删除 f1", "userId": "42"}))
    request_id, (owner_id, fut) = await _wait_pending()
    assert owner_id == "42"
    transport = ASGITransport(app=app)
    async with AsyncClient(transport=transport, base_url="http://test") as c:
        # 错误 userId 尝试批准 → 403，且 future 未被触发
        resp = await c.post(
            "/api/agent/confirm",
            json={"requestId": request_id, "approve": True, "userId": "999"},
        )
        assert resp.status_code == 200
        assert resp.json()["code"] == 403
        assert not fut.done()
        # 正确归属者批准 → 200，future 被触发，流继续
        resp = await c.post(
            "/api/agent/confirm",
            json={"requestId": request_id, "approve": True, "userId": "42"},
        )
        assert resp.status_code == 200
        assert resp.json()["code"] == 200
    events = await task

    types = [t for t, _ in events]
    assert "tool_start" in types
    assert types[-1] == "done"


@pytest.mark.asyncio
async def test_confirm_unknown_request(fake_deps):
    """确认请求不存在或已过期时返回 404。"""
    _make, _ = fake_deps
    transport = ASGITransport(app=app)
    async with AsyncClient(transport=transport, base_url="http://test") as c:
        resp = await c.post(
            "/api/agent/confirm",
            json={"requestId": "no_such_request", "approve": True, "userId": "42"},
        )
        assert resp.status_code == 200
        assert resp.json()["code"] == 404
