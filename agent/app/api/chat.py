"""SSE 流式对话接口。"""
import asyncio
import json
import uuid
from typing import Any, AsyncIterator

from fastapi import APIRouter
from fastapi.responses import StreamingResponse
from pydantic import BaseModel

from app.core.agent import Agent
from app.core.llm import LLMClient
from app.mcp.client import MCPClient
from app.schemas.common import R
from app.storage.session_store import SessionStore

router = APIRouter(prefix="/api/agent")

# 模块级单例（可被测试 monkeypatch 替换）
_llm: LLMClient | None = None
_mcp: MCPClient | None = None
_store: SessionStore | None = None

# 敏感操作确认：request_id -> (user_id, Future[bool])，等待用户通过 /confirm 接口批复
_pending_confirmations: dict[str, tuple[str, asyncio.Future[bool]]] = {}
# 用户不响应时的默认等待时长（秒），超时视为拒绝
CONFIRM_TIMEOUT_SECONDS = 120


class ChatRequest(BaseModel):
    message: str
    userId: str
    sessionId: str | None = None
    history: list[dict[str, Any]] | None = None
    # 会话级"工作目录"：前端把用户当前浏览的文件夹传下来，Agent 据此注入目录快照。
    # 缺省时 Agent 按根目录（-1）处理。
    currentDirId: str | None = None
    currentDirName: str | None = None


class ConfirmRequest(BaseModel):
    """用户对敏感操作（移动/删除）的批准结果。"""

    requestId: str
    approve: bool
    userId: str


@router.post("/confirm")
async def confirm_operation(req: ConfirmRequest) -> R:
    """提交敏感操作的批准结果，恢复被挂起的对话流。"""
    entry = _pending_confirmations.get(req.requestId)
    if entry is None:
        return R.fail("确认请求不存在或已过期", code=404)
    owner_id, fut = entry
    if owner_id != str(req.userId):
        return R.fail("无权处理该确认请求", code=403)
    if not fut.done():
        fut.set_result(req.approve)
    return R.ok(message="已批准，继续执行" if req.approve else "已取消该操作")


def _sse(event: str, data: dict[str, Any]) -> str:
    return f"event: {event}\ndata: {json.dumps(data, ensure_ascii=False)}\n\n"


@router.get("/sessions")
async def list_my_sessions(userId: str) -> R:
    """列出当前用户的历史会话（按更新时间倒序，最多 50 条）。"""
    global _store
    if _store is None:
        _store = SessionStore()
    try:
        await _store.init()
        rows = await _store.list_sessions(int(userId), limit=50)
        data = [
            {
                "sessionId": r["session_id"],
                "title": r["title"],
                "updatedAt": r["updated_at"].isoformat() if r["updated_at"] else None,
            }
            for r in rows
        ]
        return R.ok(data=data)
    except Exception as exc:
        return R.fail(f"查询会话列表失败: {exc}", code=500)


@router.get("/session/{session_id}/messages")
async def session_messages(session_id: str, userId: str) -> R:
    """查询指定会话的历史消息（校验归属，防止越权）。"""
    global _store
    if _store is None:
        _store = SessionStore()
    try:
        await _store.init()
        session = await _store.get_session(session_id)
        if session is None:
            return R.fail("会话不存在", code=404)
        if str(session["user_id"]) != str(userId):
            return R.fail("无权访问该会话", code=403)
        rows = await _store.load_history(session_id)
        return R.ok(
            data={
                "sessionId": session_id,
                "title": session["title"],
                "messages": rows,
            }
        )
    except Exception as exc:
        return R.fail(f"查询会话失败: {exc}", code=500)


async def _guard_session(session_id: str, user_id: str) -> dict[str, Any] | None:
    """校验会话归属，返回会话或 None（并已发错误响应时由调用方处理）。"""
    global _store
    if _store is None:
        _store = SessionStore()
    await _store.init()
    session = await _store.get_session(session_id)
    if session is None:
        return None
    if str(session["user_id"]) != str(user_id):
        return None
    return session


@router.get("/session/{session_id}/traces")
async def session_traces(session_id: str, userId: str) -> R:
    """列出会话内全部处理链路（概要）。"""
    global _store
    if _store is None:
        _store = SessionStore()
    try:
        session = await _guard_session(session_id, userId)
        if session is None:
            return R.fail("会话不存在或无权访问", code=403)
        rows = await _store.trace_ids_by_session(session_id, int(userId))
        # 为每个链路补上用户消息与回答，便于直接展示
        for item in rows:
            steps = await _store.trace_steps(item["traceId"])
            item["userMessage"] = next(
                (s["payload"].get("content", "") for s in steps if s["stepType"] == "message_in"),
                "",
            )
            item["answer"] = next(
                (s["payload"].get("content", "") for s in steps if s["stepType"] == "answer"),
                "",
            )
            item["hasError"] = any(s["status"] == "error" for s in steps)
        return R.ok(data=rows)
    except Exception as exc:
        return R.fail(f"查询链路失败: {exc}", code=500)


@router.get("/trace/{trace_id}")
async def trace_detail(trace_id: str, userId: str) -> R:
    """查询单条链路的完整步骤。"""
    global _store
    if _store is None:
        _store = SessionStore()
    try:
        await _store.init()
        steps = await _store.trace_steps(trace_id)
        if not steps:
            return R.fail("链路不存在", code=404)
        # 归属校验：从步骤中读取 user_id
        if str(steps[0]["userId"]) != str(userId):
            return R.fail("无权访问该链路", code=403)
        return R.ok(data={"traceId": trace_id, "steps": steps})
    except Exception as exc:
        return R.fail(f"查询链路失败: {exc}", code=500)


@router.post("/chat")
async def chat(req: ChatRequest) -> StreamingResponse:
    global _llm, _mcp, _store
    if _llm is None:
        _llm = LLMClient()
    if _mcp is None:
        _mcp = MCPClient()
    if _store is None:
        _store = SessionStore()

    agent = Agent(
        llm=_llm,
        mcp=_mcp,
        user_id=req.userId,
        current_dir_id=req.currentDirId,
        current_dir_name=req.currentDirName,
    )

    async def gen() -> AsyncIterator[str]:
        session_id = req.sessionId
        history = None
        trace_id = uuid.uuid4().hex
        pending_tool: dict[str, Any] = {}  # tool_start 暂存，等待 tool_end 配对落库
        try:
            await _store.init()
            if session_id:
                rows = await _store.load_history(session_id)
                history = [{"role": r["role"], "content": r["content"]} for r in rows]
            else:
                session_id = await _store.create_session(
                    user_id=int(req.userId), title=req.message[:50]
                )
            await _store.save_message(
                session_id, "user", req.message, trace_id=trace_id
            )
        except Exception as exc:  # 会话存储异常不阻断对话，仅降级
            session_id = req.sessionId or ""
            history = req.history

        async def trace(step_type: str, payload: dict[str, Any] | None = None,
                        step_name: str | None = None, status: str = "ok",
                        duration_ms: int | None = None) -> None:
            try:
                await _store.save_trace(
                    trace_id=trace_id,
                    session_id=session_id,
                    user_id=int(req.userId),
                    step_type=step_type,
                    payload=payload,
                    step_name=step_name,
                    status=status,
                    duration_ms=duration_ms,
                )
            except Exception:
                pass  # 链路记录失败不影响对话主流程

        async def request_confirm(
            tool_name: str, args: dict[str, Any], request_id: str
        ) -> bool:
            """等待用户通过 /confirm 接口批复；超时或会话断开视为拒绝。"""
            fut: asyncio.Future[bool] = asyncio.get_running_loop().create_future()
            _pending_confirmations[request_id] = (str(req.userId), fut)
            await trace(
                "confirmation",
                {"tool": tool_name, "arguments": args},
                step_name=tool_name,
                status="pending",
            )
            try:
                approved = await asyncio.wait_for(
                    fut, timeout=CONFIRM_TIMEOUT_SECONDS
                )
            except asyncio.TimeoutError:
                approved = False
            finally:
                _pending_confirmations.pop(request_id, None)
            await trace(
                "confirmation",
                {"tool": tool_name, "arguments": args, "approved": approved},
                step_name=tool_name,
                status="approved" if approved else "rejected",
            )
            return approved

        try:
            await trace("message_in", {"content": req.message})
            async for evt in agent.stream_run(
                req.message, history, confirm=request_confirm
            ):
                ev_type = evt["type"]
                if ev_type == "llm_decision":
                    await trace(
                        "llm_decision",
                        {"tools": evt.get("tools", []), "durationMs": evt.get("duration_ms")},
                    )
                elif ev_type == "tool_start":
                    pending_tool = {
                        "tool": evt.get("tool"),
                        "arguments": evt.get("arguments"),
                    }
                elif ev_type == "tool_end":
                    result_text = evt.get("result") or ""
                    preview = (
                        result_text[:200] + "…" if len(result_text) > 200 else result_text
                    )
                    evt = {**evt, "resultPreview": preview}
                    await trace(
                        "tool_call",
                        {
                            "tool": evt.get("tool"),
                            "arguments": pending_tool.get("arguments"),
                            "result": evt.get("result"),
                        },
                        step_name=evt.get("tool"),
                        status="error"
                        if evt.get("error")
                        else ("rejected" if evt.get("skipped") else "ok"),
                        duration_ms=evt.get("duration_ms"),
                    )
                    pending_tool = {}
                elif ev_type == "done":
                    if session_id:
                        evt = {**evt, "sessionId": session_id, "traceId": trace_id}
                    content = evt.get("content") or ""
                    if content:
                        try:
                            await _store.save_message(
                                session_id, "assistant", content, trace_id=trace_id
                            )
                        except Exception:
                            pass
                    await trace("answer", {"content": content})
                elif ev_type == "error":
                    await trace(
                        "error", {"content": evt.get("content", "")}, status="error"
                    )
                yield _sse(ev_type, evt)
        except Exception as exc:  # 任何异常都以 error 事件结束，避免连接悬挂
            yield _sse("error", {"content": f"服务异常: {exc}"})

    return StreamingResponse(gen(), media_type="text/event-stream")
