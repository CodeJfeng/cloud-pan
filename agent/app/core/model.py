"""LangChain ChatOpenAI 适配：兼容任意 OpenAI 兼容网关（DeepSeek/通义/Kimi）。"""
from langchain_openai import ChatOpenAI

from app.config import Settings
from app.core.llm_logging import LLMErrorLoggingHandler


class LLMError(RuntimeError):
    """LLM 配置/调用错误（自 llm.py 迁移，随旧客户端一并移除后保留）。"""


def build_chat_model(settings: Settings) -> ChatOpenAI:
    """按 settings 构建 ChatOpenAI；api_key 缺失时抛错并给出环境变量提示。"""
    if not (settings.llm_api_key or "").strip():
        raise LLMError(
            "未配置 LLM API Key：请设置 AGENT_LLM_API_KEY（并确认 AGENT_LLM_BASE_URL / AGENT_LLM_MODEL）后重启服务"
        )
    return ChatOpenAI(
        model=settings.llm_model,
        base_url=settings.llm_base_url,
        api_key=settings.llm_api_key,
        temperature=settings.llm_temperature,
        streaming=True,
        # 任何模型调用失败都落日志（含 trace_id），无需各节点重复 try/except
        callbacks=[LLMErrorLoggingHandler(model_name=settings.llm_model)],
    )
