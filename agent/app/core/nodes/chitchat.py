"""直答节点：问候/闲聊/能力询问由轻量会话直接回答，不进 ReAct 循环。

这类请求不需要工具、不需要目录快照，进 react 反而会被 base 的工具强约束
（"想到就调工具""先调用对应工具再回答"）与整份目录快照干扰，既浪费 token
又可能诱导模型去"先看看网盘"。此处用独立的直答提示词流式回答，SSE 契约
与 react/plan 收尾一致（delta 流式 + done）。
"""
from typing import Any

from langchain_core.messages import BaseMessage, SystemMessage

from app.core.events import emit
from app.core.prompts import CHITCHAT_SYSTEM_PROMPT

# 直答只看最近若干条消息：闲聊无需长上下文
MAX_CONTEXT_MESSAGES = 6


class ChitchatDeps:
    """直答运行时依赖（模型由图构造闭包注入，便于测试替换）。"""

    def __init__(self, model: Any):
        self.model = model


async def chitchat(state: dict, deps: ChitchatDeps) -> dict:
    """流式产出闲聊/能力介绍回答，写 final_answer 并结束本轮。"""
    conv: list[BaseMessage] = [
        SystemMessage(content=CHITCHAT_SYSTEM_PROMPT),
        *state["messages"][-MAX_CONTEXT_MESSAGES:],
    ]
    answer = ""
    async for chunk in deps.model.astream(conv):
        piece = chunk if isinstance(chunk, str) else getattr(chunk, "content", "")
        if isinstance(piece, str) and piece:
            answer += piece
            emit(type="delta", content=piece)
    emit(type="done", content=answer)
    return {"final_answer": answer, "mode": "chitchat"}
