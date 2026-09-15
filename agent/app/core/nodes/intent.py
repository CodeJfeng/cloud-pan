"""意图识别节点：由 LLM 判定用户意图、置信度与执行模式。

取代原先的关键词规则（context.classify_intent）：场景提示词已统一为一份
base.md，由模型自行判断适用哪条规则，本节点只回答"用户到底想干什么"
以及"能不能安全动手"。

输出四条分支（写入 state.mode）：
    - chitchat：问候/闲聊/能力询问，交给直答节点（无工具、无目录快照）；
    - react：意图明确且需要工具，交给 react_agent；
    - plan：包含多个可并行子任务，交给 planner；
    - clarify：意图不明或置信度不足，直接向用户追问，本轮结束。

两类失败语义不同，不可混为一谈：
    - 模型输出无法解析（没按 JSON 格式答）→ 视为"没答清楚"，走 clarify 追问；
    - 模型调用异常（网络/网关故障）→ 记日志并**降级 react**，绝不伪装成
      "意图不明"让用户重新描述需求。
"""
import logging
from typing import Any, Literal

from langchain_core.messages import BaseMessage, SystemMessage
from pydantic import BaseModel, Field

from app.core.events import emit
from app.core.prompts import CLARIFY_MESSAGE, INTENT_SYSTEM_PROMPT
from app.core.structured import invoke_structured

logger = logging.getLogger(__name__)

# 置信度下限：低于此值不猜，改为向用户追问
CONFIDENCE_THRESHOLD = 0.6
# 意图判定只看最近若干条消息：长会话无需全量上下文，省 token
MAX_CONTEXT_MESSAGES = 6

# 允许输出的意图标签（与 roles/intent.md 保持一致；仅用于链路记录与人工判读，
# 不再用于切换提示词）
_INTENTS = frozenset(
    {"search", "browse", "file_ops", "share", "recycle", "chitchat", "unclear"}
)


class IntentDecision(BaseModel):
    """意图识别结构化输出。"""

    intent: Literal[
        "search", "browse", "file_ops", "share", "recycle", "chitchat", "unclear"
    ] = Field(description="用户意图标签")
    confidence: float = Field(default=0.0, ge=0.0, le=1.0, description="判定置信度，0~1")
    mode: Literal["react", "plan"] = Field(
        default="react", description="执行模式：多步可并行用 plan，其余用 react"
    )
    clarify: str = Field(
        default="", description="confidence 低于 0.6 或意图不明时，给用户的追问（一句话）"
    )
    reason: str = Field(default="", description="判定理由，便于排查")


class IntentDeps:
    """意图识别运行时依赖（模型由图构造闭包注入，便于测试替换）。"""

    def __init__(self, model: Any, allow_plan: bool = True, allow_chitchat: bool = True):
        self.model = model
        # 模型不支持结构化输出时 planner 不可用，plan 判定降级为 react
        self.allow_plan = allow_plan
        # 模型不支持流式输出时直答不可用，chitchat 降级为 react
        self.allow_chitchat = allow_chitchat


def _clarify_result(question: str = "") -> dict:
    return {
        "intent": "unclear",
        "confidence": 0.0,
        "mode": "clarify",
        "clarify_question": (question or "").strip() or CLARIFY_MESSAGE,
    }


async def intent(state: dict, deps: "IntentDeps") -> dict:
    """调模型判定意图；低置信度/判定失败 → 走澄清分支。"""
    conv: list[BaseMessage] = [
        SystemMessage(content=INTENT_SYSTEM_PROMPT),
        *state["messages"][-MAX_CONTEXT_MESSAGES:],
    ]
    try:
        decision: IntentDecision | None = await invoke_structured(
            deps.model, conv, IntentDecision
        )
    except Exception as exc:  # 网关/网络故障：降级 react，不打扰用户
        logger.warning("意图识别调用失败，降级为 react：%s", exc)
        return {"intent": "unknown", "confidence": 0.0, "mode": "react"}
    if decision is None:  # 模型没按 JSON 格式答：追问
        return _clarify_result()

    name = decision.intent if decision.intent in _INTENTS else "unclear"
    confidence = max(0.0, min(1.0, float(decision.confidence or 0.0)))
    if name == "unclear" or confidence < CONFIDENCE_THRESHOLD:
        return _clarify_result(decision.clarify)

    if name == "chitchat" and deps.allow_chitchat:
        return {"intent": name, "confidence": confidence, "mode": "chitchat"}
    mode = "plan" if (decision.mode == "plan" and deps.allow_plan) else "react"
    return {"intent": name, "confidence": confidence, "mode": mode}


def route_after_intent(state: dict) -> str:
    """按意图判定结果分流：直答 / 澄清 / 规划 / react。"""
    mode = state.get("mode")
    if mode == "plan":
        return "planner"
    if mode == "clarify":
        return "clarify"
    if mode == "chitchat":
        return "chitchat"
    return "react_agent"


async def clarify(state: dict) -> dict:
    """意图不明：把追问作为本轮最终回答直接返回，等用户补充后再走完整流程。"""
    question = state.get("clarify_question") or CLARIFY_MESSAGE
    # 复用既有 SSE 契约（delta + done）：前端无需改动，追问内容照常入会话与链路
    emit(type="delta", content=question)
    emit(type="done", content=question)
    return {"final_answer": question, "mode": "clarify"}
