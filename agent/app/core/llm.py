"""OpenAI 兼容协议 LLM 客户端（httpx 手写，无重依赖）。"""
import json
from typing import Any, AsyncIterator, Iterable

import httpx

from app.config import settings


class LLMError(RuntimeError):
    pass


class LLMClient:
    """面向 OpenAI 兼容接口的流式/非流式客户端。

    base_url 可指向 DeepSeek / 通义 / Kimi / OpenAI 等任意 OpenAI 兼容网关。
    """

    def __init__(
        self,
        base_url: str | None = None,
        api_key: str | None = None,
        model: str | None = None,
        timeout: float = 60.0,
        _transport: httpx.AsyncBaseTransport | None = None,
    ) -> None:
        self.base_url = (base_url or settings.llm_base_url).rstrip("/")
        self.api_key = (api_key or settings.llm_api_key or "").strip()
        self.model = model or settings.llm_model
        if not self.api_key:
            raise LLMError(
                "未配置 LLM API Key：请设置环境变量 AGENT_LLM_API_KEY（并确认 AGENT_LLM_BASE_URL / AGENT_LLM_MODEL）后重启服务"
            )
        self._client = httpx.AsyncClient(
            base_url=self.base_url,
            headers={
                # 校验 key 首尾空白，避免生成以空白结尾的非法 header 值
                "Authorization": f"Bearer {self.api_key}",
                "Content-Type": "application/json",
            },
            timeout=timeout,
            transport=_transport,
        )
        # 最近一次工具调用结果，供 Agent 循环读取
        self.last_tool_calls: list[dict[str, Any]] = []

    async def chat(
        self,
        messages: Iterable[dict[str, Any]],
        tools: list[dict[str, Any]] | None = None,
        temperature: float = 0.7,
    ) -> str | None:
        """非流式对话。返回 assistant content；若是工具调用返回 None 并存到 last_tool_calls。"""
        payload: dict[str, Any] = {
            "model": self.model,
            "messages": list(messages),
            "temperature": temperature,
        }
        if tools:
            payload["tools"] = tools

        resp = await self._client.post("/chat/completions", json=payload)
        if resp.status_code != 200:
            raise LLMError(f"LLM 请求失败: {resp.status_code} {resp.text[:200]}")
        data = resp.json()
        self._capture_tool_calls(data)

        choice = data["choices"][0]
        return choice.get("message", {}).get("content")

    async def stream(
        self,
        messages: Iterable[dict[str, Any]],
        tools: list[dict[str, Any]] | None = None,
        temperature: float = 0.7,
    ) -> AsyncIterator[str]:
        """流式对话，逐段产出 content 增量。工具调用场景请使用 chat()。"""
        payload: dict[str, Any] = {
            "model": self.model,
            "messages": list(messages),
            "temperature": temperature,
            "stream": True,
        }
        if tools:
            payload["tools"] = tools

        async with self._client.stream("POST", "/chat/completions", json=payload) as resp:
            if resp.status_code != 200:
                text = await resp.aread()
                raise LLMError(f"LLM 流式请求失败: {resp.status_code} {text[:200]}")
            async for line in resp.aiter_lines():
                if not line.startswith("data:"):
                    continue
                raw = line[len("data:"):].strip()
                if raw == "[DONE]":
                    break
                try:
                    chunk = json.loads(raw)
                except json.JSONDecodeError:
                    continue
                delta = chunk.get("choices", [{}])[0].get("delta", {})
                if delta.get("content"):
                    yield delta["content"]

    def _capture_tool_calls(self, data: dict[str, Any]) -> None:
        self.last_tool_calls = []
        choice = data.get("choices", [{}])[0]
        for tc in choice.get("message", {}).get("tool_calls", []):
            fn = tc.get("function", {})
            self.last_tool_calls.append(
                {
                    "id": tc.get("id"),
                    "type": tc.get("type", "function"),
                    "function": {
                        "name": fn.get("name"),
                        "arguments": fn.get("arguments", "{}"),
                    },
                }
            )

    async def aclose(self) -> None:
        await self._client.aclose()
