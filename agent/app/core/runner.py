"""Graph → SSE 事件适配层：维护确认注册表；interrupt 挂起、Command(resume) 恢复同一 thread。

节点经 get_stream_writer 发自定义事件（app.core.events.emit），此处以
stream_mode=["custom", "updates"] 消费；敏感工具确认由节点 interrupt()
挂起整图，runner 把中断值翻译为 confirmation_required SSE 事件并等待
/confirm 批复（未来由 chat 层路由），超时视为拒绝后以 Command(resume=…) 恢复。
"""
import asyncio
import uuid
from typing import Any, AsyncIterator

from langgraph.types import Command

# 用户不响应确认时的默认等待时长（秒），超时视为拒绝（与旧 chat 语义一致）
CONFIRM_TIMEOUT_SECONDS = 120
# request_id -> (user_id, asyncio.Future[bool])；/confirm endpoint 从 runner 模块读取
_pending_confirmations: dict[str, tuple[str, asyncio.Future[bool]]] = {}


def get_confirmation(request_id: str) -> tuple[str, asyncio.Future[bool]] | None:
    """供 /confirm endpoint 查询挂起的确认请求（不存在返回 None）。"""
    return _pending_confirmations.get(request_id)


async def run_agent_stream(
    *,
    graph: Any,
    inputs: dict[str, Any],
    config: dict[str, Any],
    user_id: str,
) -> AsyncIterator[dict[str, Any]]:
    """按 thread 驱动 graph 直至完成；每次 interrupt 挂起等待批复后恢复。

    产出事件 dict（type ∈ SSE_TYPES）。confirmation_required 由 runner 生成，
    其余（thinking/llm_decision/tool_*/delta/done）转发节点自定义事件。
    """
    loop = asyncio.get_running_loop()
    payload: Any = inputs
    while True:
        interrupted: Any = None
        async for chunk in graph.astream(
            payload,
            config=config,
            stream_mode=["custom", "updates"],
        ):
            if not isinstance(chunk, tuple):
                continue
            mode, data = chunk
            if mode == "updates" and isinstance(data, dict) and "__interrupt__" in data:
                interrupted = data["__interrupt__"][0]  # Interrupt(value)
                break
            if mode == "custom" and isinstance(data, dict) and data.get("type"):
                yield data
        if interrupted is None:
            return

        value: dict[str, Any] = interrupted.value or {}
        rid = value.get("requestId") or uuid.uuid4().hex
        fut: asyncio.Future[bool] = loop.create_future()
        _pending_confirmations[rid] = (str(user_id), fut)
        yield {
            "type": "confirmation_required",
            "requestId": rid,
            "tool": value.get("tool"),
            # arguments 是工具真正使用的加密参数（执行不变）；
            # display 是翻译后的可读描述（文件名/目标目录），仅供前端展示
            "arguments": value.get("arguments"),
            "display": value.get("display"),
        }
        try:
            approved = await asyncio.wait_for(fut, timeout=CONFIRM_TIMEOUT_SECONDS)
        except asyncio.TimeoutError:
            approved = False  # 超时视为拒绝，与旧语义一致
        finally:
            _pending_confirmations.pop(rid, None)
        payload = Command(resume=approved)
