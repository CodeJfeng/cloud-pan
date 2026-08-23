"""MCP 客户端：Streamable HTTP 传输（JSON-RPC over HTTP + SSE 解析）。

不依赖 mcp SDK（部分环境该包被裁剪），直接用 httpx 手写协议，
与 Java MCP Server（HttpServletStreamableServerTransportProvider）对齐。

userId 为内部参数：转换 OpenAI schema 时剥离，call_tool 时自动注入。
"""
import json
from dataclasses import dataclass
from typing import Any

import httpx

from app.config import settings

PROTOCOL_VERSION = "2025-03-26"
_HEADERS = {"Accept": "application/json, text/event-stream"}


@dataclass
class McpToolInfo:
    name: str
    description: str
    input_schema: dict[str, Any]


class MCPClientError(RuntimeError):
    pass


class MCPSessionExpiredError(MCPClientError):
    """MCP 会话失效（Server 重启/会话过期），需重新握手。"""


def _parse_response_body(content: bytes, content_type: str) -> dict[str, Any]:
    """解析 Streamable HTTP 响应：可能是纯 JSON，也可能是 SSE 封装。"""
    text = content.decode("utf-8")
    if "text/event-stream" in content_type:
        data_lines = []
        for line in text.splitlines():
            if line.startswith("data:"):
                data_lines.append(line[5:].strip())
        if not data_lines:
            raise MCPClientError(f"MCP 响应无 data 内容: {text[:200]}")
        return json.loads("".join(data_lines))
    return json.loads(text)


class MCPClient:
    """Java MCP Server 的 Streamable HTTP 客户端。"""

    def __init__(self, url: str | None = None, timeout: float = 30.0) -> None:
        self.url = url or settings.mcp_server_url
        self.timeout = timeout
        self._client: httpx.AsyncClient | None = None
        self._session_id: str | None = None
        self._initialized = False

    async def connect(self) -> None:
        if self._initialized:
            return
        if self._client is None:
            self._client = httpx.AsyncClient(timeout=self.timeout)
        await self._post(
            "initialize",
            {
                "protocolVersion": PROTOCOL_VERSION,
                "capabilities": {},
                "clientInfo": {"name": "cloud-pan-agent", "version": "0.1.0"},
            },
        )
        # 发送 initialized 通知（兼容要求通知的 Server）
        try:
            await self._post(
                "notifications/initialized", {}, notification=True
            )
        except Exception:
            pass
        self._initialized = True

    async def close(self) -> None:
        if self._client is not None:
            await self._client.aclose()
            self._client = None
        self._session_id = None

    async def _post(
        self,
        method: str,
        params: dict[str, Any],
        notification: bool = False,
    ) -> dict[str, Any]:
        if self._client is None:
            raise MCPClientError("MCP client 未连接，请先调用 connect()")
        payload: dict[str, Any] = {"jsonrpc": "2.0", "method": method}
        if not notification:
            payload["id"] = 1
        if params:
            payload["params"] = params

        headers = dict(_HEADERS)
        if self._session_id:
            headers["Mcp-Session-Id"] = self._session_id

        resp = await self._client.post(
            self.url, json=payload, headers=headers
        )
        if resp.status_code not in (200, 202):
            if resp.status_code == 404 and "Session not found" in resp.text:
                raise MCPSessionExpiredError(
                    f"MCP 会话失效 {method}: HTTP {resp.status_code} {resp.text[:200]}"
                )
            raise MCPClientError(
                f"MCP 请求失败 {method}: HTTP {resp.status_code} {resp.text[:200]}"
            )
        # 会话 ID 从首次响应头获取
        sid = resp.headers.get("Mcp-Session-Id")
        if sid:
            self._session_id = sid

        if resp.status_code == 202 or notification:
            return {}

        body = _parse_response_body(resp.content, resp.headers.get("content-type", ""))
        if "error" in body:
            err = body["error"]
            if "Session not found" in str(err.get("message", "")):
                raise MCPSessionExpiredError(
                    f"MCP 会话失效 {method}: {err.get('code')} {err.get('message', '')}"
                )
            raise MCPClientError(
                f"MCP 请求错误 {method}: {err.get('code')} {err.get('message', '')}"
            )
        return body.get("result", {})

    async def _post_with_retry(
        self,
        method: str,
        params: dict[str, Any],
        notification: bool = False,
    ) -> dict[str, Any]:
        """请求 MCP；若会话失效则重置会话并重新握手后重试一次。"""
        try:
            return await self._post(method, params, notification=notification)
        except MCPSessionExpiredError:
            # Server 重启导致会话失效：重置状态并重新 initialize
            self._initialized = False
            self._session_id = None
            await self.connect()
            return await self._post(method, params, notification=notification)

    async def list_tools(self) -> list[McpToolInfo]:
        result = await self._post_with_retry("tools/list", {})
        return [
            McpToolInfo(
                name=t.get("name", ""),
                description=t.get("description", ""),
                input_schema=t.get("inputSchema", {}),
            )
            for t in result.get("tools", [])
        ]

    async def call_tool(self, name: str, arguments: dict[str, Any], user_id: str) -> str:
        """调用 MCP 工具，自动注入内部参数 userId。"""
        payload = dict(arguments)
        payload["userId"] = user_id
        result = await self._post_with_retry("tools/call", {"name": name, "arguments": payload})
        texts = [
            c.get("text", "")
            for c in result.get("content", [])
            if c.get("type") == "text" and c.get("text")
        ]
        text = "\n".join(texts) or "（无文本返回）"
        if result.get("isError"):
            raise MCPClientError(f"工具 {name} 执行失败: {text}")
        return text

    @staticmethod
    def to_openai_tools(tools: list[McpToolInfo]) -> list[dict[str, Any]]:
        """MCP 工具 → OpenAI function calling schema（剥离内部参数 userId）。"""
        openai_tools = []
        for t in tools:
            input_schema = getattr(t, "input_schema", None) or getattr(
                t, "inputSchema", {}
            )
            schema: dict[str, Any] = {"type": "object"}
            props = dict(input_schema.get("properties", {}))
            props.pop("userId", None)
            schema["properties"] = props
            required = [k for k in input_schema.get("required", []) if k != "userId"]
            if required:
                schema["required"] = required
            openai_tools.append(
                {
                    "type": "function",
                    "function": {
                        "name": t.name,
                        "description": t.description,
                        "parameters": schema,
                    },
                }
            )
        return openai_tools
