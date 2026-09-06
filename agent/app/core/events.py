"""节点内自定义事件发射；runner 侧映射为对外 SSE 事件。

对外 SSE 事件名集合（迁移不变量）：thinking / llm_decision /
confirmation_required / tool_start / tool_end / delta / done / error。
"""
from langgraph.config import get_stream_writer

SSE_TYPES = frozenset(
    {
        "thinking",
        "llm_decision",
        "confirmation_required",
        "tool_start",
        "tool_end",
        "delta",
        "done",
        "error",
    }
)


def emit(**data) -> None:
    """向 runner 的自定义流通道发事件（type 须为 SSE_TYPES 之一）。"""
    assert data.get("type") in SSE_TYPES, data.get("type")
    get_stream_writer().emit(data)
