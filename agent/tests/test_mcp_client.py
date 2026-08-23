import json

import httpx
import pytest

from app.mcp.client import MCPClient, McpToolInfo

TOOLS_RESP = {
    "tools": [
        {
            "name": "list_files",
            "description": "列出文件夹内容",
            "inputSchema": {
                "type": "object",
                "properties": {
                    "userId": {"type": "string", "description": "内部参数"},
                    "parentId": {"type": "string", "description": "父文件夹ID"},
                },
                "required": ["userId", "parentId"],
            },
        },
        {
            "name": "search_files",
            "description": "搜索文件",
            "inputSchema": {
                "type": "object",
                "properties": {
                    "userId": {"type": "string"},
                    "keyword": {"type": "string"},
                },
                "required": ["userId", "keyword"],
            },
        },
    ]
}

INIT_RESP = {
    "jsonrpc": "2.0",
    "id": 1,
    "result": {
        "protocolVersion": "2025-03-26",
        "capabilities": {"tools": {}},
        "serverInfo": {"name": "pan-mcp", "version": "1.0.0"},
    },
}


def make_client(handler):
    client = MCPClient(url="http://mcp.test/mcp")
    client._client = httpx.AsyncClient(transport=httpx.MockTransport(handler))
    return client


def _standard_handler(extra):
    """标准 MCP Server mock：initialize/notifications/tools/list/tools/call。"""

    def handler(request: httpx.Request) -> httpx.Response:
        body = json.loads(request.content)
        method = body.get("method")
        if method == "initialize":
            assert "Mcp-Session-Id" not in request.headers
            return httpx.Response(
                200,
                headers={"Mcp-Session-Id": "sess-1"},
                json=INIT_RESP,
            )
        if method == "notifications/initialized":
            assert request.headers.get("Mcp-Session-Id") == "sess-1"
            return httpx.Response(202)
        if method == "tools/list":
            assert request.headers.get("Mcp-Session-Id") == "sess-1"
            # SSE 封装格式
            sse = (
                "event: message\ndata: "
                + json.dumps(
                    {"jsonrpc": "2.0", "id": 1, "result": TOOLS_RESP},
                    ensure_ascii=False,
                )
                + "\n\n"
            )
            return httpx.Response(
                200, headers={"content-type": "text/event-stream"}, content=sse
            )
        if method == "tools/call":
            return extra(request, body)
        return httpx.Response(500)

    return handler


@pytest.mark.asyncio
async def test_connect_and_list_tools():
    client = make_client(_standard_handler(extra=lambda r, b: httpx.Response(500)))
    await client.connect()
    assert client._session_id == "sess-1"

    tools = await client.list_tools()
    assert [t.name for t in tools] == ["list_files", "search_files"]
    assert tools[0].input_schema["required"] == ["userId", "parentId"]
    await client.close()


@pytest.mark.asyncio
async def test_to_openai_tools_strips_user_id():
    tools = [McpToolInfo(t["name"], t["description"], t["inputSchema"]) for t in TOOLS_RESP["tools"]]
    openai_tools = MCPClient.to_openai_tools(tools)
    assert len(openai_tools) == 2
    schema = openai_tools[0]["function"]["parameters"]
    assert "userId" not in schema["properties"]
    assert "userId" not in schema["required"]
    assert schema["required"] == ["parentId"]


@pytest.mark.asyncio
async def test_call_tool_injects_user_id():
    seen = {}

    def on_call(request, body):
        seen["args"] = body["params"]["arguments"]
        assert body["params"]["name"] == "list_files"
        assert body["params"]["arguments"]["userId"] == "42"
        return httpx.Response(
            200,
            json={
                "jsonrpc": "2.0",
                "id": 1,
                "result": {
                    "content": [{"type": "text", "text": "ok"}],
                    "isError": False,
                },
            },
        )

    client = make_client(_standard_handler(extra=on_call))
    await client.connect()
    result = await client.call_tool("list_files", {"parentId": "abc"}, user_id="42")
    assert result == "ok"
    assert seen["args"]["parentId"] == "abc"
    await client.close()


@pytest.mark.asyncio
async def test_call_tool_error_raises():
    def on_call(request, body):
        return httpx.Response(
            200,
            json={
                "jsonrpc": "2.0",
                "id": 1,
                "result": {
                    "content": [{"type": "text", "text": "没有权限"}],
                    "isError": True,
                },
            },
        )

    client = make_client(_standard_handler(extra=on_call))
    await client.connect()
    with pytest.raises(Exception, match="没有权限"):
        await client.call_tool("list_files", {"parentId": "abc"}, user_id="42")
    await client.close()
