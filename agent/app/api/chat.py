"""SSE 流式对话接口（LangGraph runner 驱动）。

每次请求在会话存储就绪后：构建图 → 预取目录快照（若指定 currentDirId）→
组装消息输入 → 由 app.core.runner.run_agent_stream 逐条消费自定义事件并转 SSE。

敏感操作确认：节点以 interrupt 挂起整图，runner 把中断值翻译为
confirmation_required SSE；注册表存于 app.core.runner（/confirm 读取）。
"""
import json
import logging
import uuid
from typing import Any, AsyncIterator

from fastapi import APIRouter
from fastapi.responses import StreamingResponse
from langchain_core.messages import AIMessage, BaseMessage, HumanMessage
from pydantic import BaseModel

from app.config import settings
from app.core import runner as agent_runner
from app.core.checkpoint import build_checkpointer, open_mysql_checkpointer
from app.core.context import _format_directory_context, _parse_file_list
from app.core.graph import build_agent_graph
from app.core.logging_config import set_trace_id
from app.core.model import build_chat_model
from app.mcp.client import MCPClient
from app.schemas.common import R
from app.storage.session_store import SessionStore

logger = logging.getLogger(__name__)

router = APIRouter(prefix="/api/agent")

# 模块级单例（可被测试 monkeypatch 替换）
_model: Any | None = None
_mcp: MCPClient | None = None
_store: SessionStore | None = None
_checkpointer: Any | None = None


class ChatRequest(BaseModel):
    message: str
    userId: str
    sessionId: str | None = None
    history: list[dict[str, Any]] | None = None
    # 会话级"工作目录"：前端把用户当前浏览的文件夹传下来，据此注入目录快照
    currentDirId: str | None = None
    currentDirName: str | None = None


class ConfirmRequest(BaseModel):
    """用户对敏感操作（移动/删除）的批准结果。"""

    requestId: str
    approve: bool
    userId: str


@router.post("/confirm")
async def confirm_operation(req: ConfirmRequest) -> R:
    """提交敏感操作的批准结果，恢复被挂起的对话流。

    确认注册表已迁至 app.core.runner（与 LLM 图在同一生命周期，确保跨
    HTTP 请求可解析到正在等待的 Future）。
    """
    entry = agent_runner.get_confirmation(req.requestId)
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


def _history_to_messages(
    history: list[dict[str, Any]] | None,
) -> list[BaseMessage]:
    """把历史消息（user/assistant 文本）转 LangChain 消息，供图输入。"""
    msgs: list[BaseMessage] = []
    for item in history or []:
        role = item.get("role")
        content = item.get("content") or ""
        if role == "user":
            msgs.append(HumanMessage(content=content))
        elif role == "assistant":
            msgs.append(AIMessage(content=content))
    return msgs


@router.post("/chat")
async def chat(req: ChatRequest) -> StreamingResponse:
    async def gen() -> AsyncIterator[str]:
        global _mcp, _store, _checkpointer
        if _store is None:
            _store = SessionStore()
        session_id = req.sessionId
        history = None
        trace_id = uuid.uuid4().hex
        pending_tool: dict[str, Any] = {}  # tool_start 暂存，等待 tool_end 配对落库
        pending_conf: dict[str, dict[str, Any]] = {}  # 确认请求 → 待收尾事件
        try:
            await _store.init()
            if session_id:
                rows = await _store.load_history(session_id)
                history = [{"role": r["role"], "content": r["content"]} for r in rows]
            else:
                session_id = await _store.create_session(
                    user_id=int(req.userId), title=req.message[:50]
                )
            await _store.save_message(session_id, "user", req.message, trace_id=trace_id)
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

        async def finalize_confirmation(tool_name: str, approved: bool) -> None:
            """按工具名把已收尾的确认请求写成 approved/rejected 链路步骤。"""
            rid = next(
                (r for r, c in pending_conf.items() if c.get("tool") == tool_name),
                None,
            )
            if rid is None:
                return
            conf = pending_conf.pop(rid)
            await trace(
                "confirmation",
                {
                    "tool": conf.get("tool"),
                    "arguments": conf.get("arguments"),
                    "approved": approved,
                },
                step_name=conf.get("tool"),
                status="approved" if approved else "rejected",
            )

        try:
            # 服务就绪：模型 / MCP / checkpointer（可被测试替换）
            model = _model if _model is not None else build_chat_model(settings)
            if _mcp is None:
                _mcp = MCPClient()
            mcp = _mcp
            if _checkpointer is None:
                try:
                    # 生产：进程级 MySQL checkpointer（跨重启断点由 thread 承载）
                    _checkpointer = await open_mysql_checkpointer()
                except Exception:
                    # MySQL 暂不可用（如单测/无库环境）时降级内存版，保证请求不因断点失败
                    _checkpointer = build_checkpointer(force_memory=True)
            checkpointer = _checkpointer
            await mcp.connect()

            # 目录快照预取：未指定目录时默认预取根目录（parentId=-1），agent 从根目录出发
            snapshot_dir_id = str(req.currentDirId) if req.currentDirId else "-1"
            snapshot_dir_name = req.currentDirName or "根目录"
            directory_context = None
            try:
                raw = await mcp.call_tool(
                    "list_files", {"parentId": snapshot_dir_id}, str(req.userId)
                )
                items = _parse_file_list(str(raw))
                directory_context = _format_directory_context(
                    snapshot_dir_id, snapshot_dir_name, items
                )
            except Exception:
                directory_context = None

            graph = await build_agent_graph(
                model=model,
                mcp=mcp,
                user_id=str(req.userId),
                checkpointer=checkpointer,
            )
            messages = _history_to_messages(history)
            messages.append(HumanMessage(content=req.message))
            inputs: dict[str, Any] = {
                "messages": messages,
                "user_input": req.message,
                "directory_context": directory_context,
            }
            config = {
                "recursion_limit": settings.graph_recursion_limit,
                "configurable": {"thread_id": uuid.uuid4().hex},
            }

            await trace("message_in", {"content": req.message})
            async for evt in agent_runner.run_agent_stream(
                graph=graph, inputs=inputs, config=config, user_id=str(req.userId)
            ):
                ev_type = evt["type"]
                if ev_type == "llm_decision":
                    await trace(
                        "llm_decision",
                        {"tools": evt.get("tools", []), "durationMs": evt.get("duration_ms")},
                    )
                elif ev_type == "confirmation_required":
                    rid = evt.get("requestId")
                    if rid:
                        pending_conf[rid] = {
                            "tool": evt.get("tool"),
                            "arguments": evt.get("arguments"),
                        }
                        await trace(
                            "confirmation",
                            {"tool": evt.get("tool"), "arguments": evt.get("arguments")},
                            step_name=evt.get("tool"),
                            status="pending",
                        )
                elif ev_type == "tool_start":
                    # 敏感操作已获批复：对应的确认步骤落库为 approved
                    await finalize_confirmation(evt.get("tool"), approved=True)
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
                    if evt.get("skipped"):
                        await finalize_confirmation(evt.get("tool"), approved=False)
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
                    pending_conf.clear()
                yield _sse(ev_type, evt)
        except Exception as exc:  # 任何异常都以 error 事件结束，避免连接悬挂
            logger.exception("对话处理异常")
            await trace("error", {"content": str(exc)}, status="error")
            yield _sse("error", {"content": f"服务异常: {exc}"})

    return StreamingResponse(gen(), media_type="text/event-stream")
