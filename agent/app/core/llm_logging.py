"""LLM 调用错误回调：任何模型调用失败都会落日志，便于线上排查。

挂在 app/core/model.py 构造的聊天模型上，因此意图识别 / planner / react /
worker / summarizer / 直答的所有调用都被覆盖，不必在每个节点重复 try/except。

记录内容：模型名、run_id、异常类型与完整错误信息（网关返回体通常就在里面，
例如 DeepSeek 的 "Prompt must contain the word 'json' ..."），以及当前请求的
trace_id（由日志格式统一注入，可与链路表 agent_trace.traceId 对齐）。

实现为同步回调：LangChain 在异步调用路径上会把同步 handler 适配后调用，
因此 async/sync 两种调用都能覆盖。
"""
import logging

from langchain_core.callbacks import BaseCallbackHandler

logger = logging.getLogger(__name__)

# 错误文本上限：避免把整页 HTML 错误或超长响应体写进日志
MAX_ERROR_CHARS = 2000


class LLMErrorLoggingHandler(BaseCallbackHandler):
    """把 LLM 调用异常写入日志（错误级）。"""

    def __init__(self, model_name: str = "-"):
        super().__init__()
        self._model_name = model_name

    def on_llm_error(self, error: BaseException, **kwargs) -> None:
        model = (
            (kwargs.get("invocation_params") or {}).get("model")
            or (kwargs.get("metadata") or {}).get("ls_model_name")
            or self._model_name
        )
        logger.error(
            "LLM 调用失败 | model=%s | run_id=%s | %s: %s",
            model,
            kwargs.get("run_id", "-"),
            type(error).__name__,
            str(error)[:MAX_ERROR_CHARS],
        )
