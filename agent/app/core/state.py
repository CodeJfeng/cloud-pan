"""LangGraph AgentState。messages 用 add_messages 归约，worker 结果按 step_id 累加。"""
import operator
from typing import Annotated, TypedDict

from langchain_core.messages import BaseMessage
from langgraph.graph.message import add_messages


def add_results(left: list[dict], right: list[dict]) -> list[dict]:
    """按 step_id 合并子任务结果（同一步后到者覆盖，天然幂等）。"""
    out = {r["step_id"]: r for r in left}
    for r in right:
        out[r["step_id"]] = r
    return list(out.values())


class AgentState(TypedDict):
    messages: Annotated[list[BaseMessage], add_messages]  # 完整对话上下文（含系统提示词）
    user_input: str
    mode: str                      # "react" | "plan" | "clarify"（意图判定结果）
    intent: str                    # 意图标签（search/browse/file_ops/share/recycle/chitchat/unclear）
    confidence: float              # 意图判定置信度 0~1
    clarify_question: str          # 意图不明时给用户的追问
    steps: list[dict]              # planner 输出的子任务
    results: Annotated[list[dict], add_results]  # {step_id, status, result}*
    final_answer: str
    error: str
    retries: int
