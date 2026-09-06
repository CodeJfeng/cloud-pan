from langchain_core.messages import AIMessage, HumanMessage
from langgraph.checkpoint.memory import MemorySaver

from app.core.checkpoint import build_checkpointer
from app.core.events import SSE_TYPES, emit
from app.core.state import AgentState


def test_state_keys_exist():
    required = {"messages", "mode", "user_input", "steps", "results", "final_answer", "error"}
    assert required.issubset(AgentState.__annotations__)


def test_events_contract():
    # 对外 SSE 事件名集合保持不变
    assert SSE_TYPES == {
        "thinking",
        "llm_decision",
        "confirmation_required",
        "tool_start",
        "tool_end",
        "delta",
        "done",
        "error",
    }


def test_checkpointer_default_is_memory_in_test():
    cp = build_checkpointer(force_memory=True)
    assert isinstance(cp, MemorySaver)
