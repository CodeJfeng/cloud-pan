"""结构化输出的网关无关实现：提示词约定 JSON + 文本解析。

不使用 langchain 的 with_structured_output，原因是网关差异会直接 400：
- DeepSeek `method="json_mode"` 会设置 response_format=json_object，而 DeepSeek
  要求 prompt 中必须出现 "json" 字样，否则报
  "Prompt must contain the word 'json' in some form ..."；
- 思考模式模型（如 deepseek-v4-flash）禁止指定 tool_choice，
  `method="function_calling"` 会报 "Thinking mode does not support this tool_choice"。

改为让模型直接输出 JSON 文本后自行解析：只依赖"模型能输出文本"，换网关不会
静默失效，失败也能被日志与调用方看见。

失败语义（重要）：
- 模型调用异常（网络/网关故障）→ 原样抛出，由调用方决定降级，避免把基础设施
  故障伪装成"模型没答清楚"而误导用户重新描述需求；
- 模型输出无法解析为 JSON / 不满足 schema → 返回 None，由调用方追问或报错。
"""
import json
import logging
import re
from typing import Any, TypeVar

from pydantic import BaseModel, ValidationError

logger = logging.getLogger(__name__)

# 模型可能把 JSON 包在 markdown 代码块里（```json ... ```）
_FENCE_RE = re.compile(r"```(?:json)?\s*(.+?)\s*```", re.S)
# 兜底：从解释性文字中抓取首个 JSON 对象（贪婪到最后一个 }，兼容嵌套）
_OBJECT_RE = re.compile(r"\{.*\}", re.S)

T = TypeVar("T", bound=BaseModel)


def extract_json_object(text: str) -> str | None:
    """从模型输出中提取 JSON 对象文本：优先代码块，其次首个 {...}。"""
    if not text:
        return None
    fenced = _FENCE_RE.search(text)
    if fenced:
        text = fenced.group(1)
    match = _OBJECT_RE.search(text)
    return match.group(0) if match else None


def _text_of(resp: Any) -> str:
    """取模型输出正文（兼容多模态分片与纯字符串）。"""
    content = getattr(resp, "content", resp)
    if isinstance(content, list):
        content = "".join(
            part.get("text", "") if isinstance(part, dict) else str(part)
            for part in content
        )
    return content if isinstance(content, str) else str(content)


async def invoke_structured(model: Any, messages: list[Any], schema: type[T]) -> T | None:
    """调用模型并要求返回 schema 的 JSON 表示。

    返回 None 表示模型输出无法解析（已记日志）；模型调用异常原样向上抛出。
    测试替身直接返回 pydantic 实例时跳过解析。
    """
    resp = await model.ainvoke(messages)
    if isinstance(resp, schema):
        return resp
    raw = extract_json_object(_text_of(resp))
    if raw is None:
        logger.warning("结构化输出未包含 JSON 对象：%s", _text_of(resp)[:200])
        return None
    try:
        return schema.model_validate(json.loads(raw))
    except (json.JSONDecodeError, ValidationError) as exc:
        logger.warning(
            "结构化输出解析失败(%s)：%s | 原文：%s", type(exc).__name__, exc, raw[:300]
        )
        return None
