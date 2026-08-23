import json

import httpx
import pytest

from app.core.llm import LLMClient


def _mock_client(handler):
    transport = httpx.MockTransport(handler)
    return LLMClient(
        base_url="http://llm.test/v1",
        api_key="test-key",
        model="test-model",
        _transport=transport,
    )


@pytest.mark.asyncio
async def test_chat_completion():
    def handler(request: httpx.Request) -> httpx.Response:
        body = json.loads(request.content)
        assert body["model"] == "test-model"
        return httpx.Response(
            200,
            json={
                "choices": [
                    {
                        "message": {"role": "assistant", "content": "你好，我是助手"},
                        "finish_reason": "stop",
                    }
                ]
            },
        )

    client = _mock_client(handler)
    resp = await client.chat([{"role": "user", "content": "hi"}])
    assert resp == "你好，我是助手"


@pytest.mark.asyncio
async def test_chat_with_tools():
    tools = [{"type": "function", "function": {"name": "search", "parameters": {}}}]

    def handler(request: httpx.Request) -> httpx.Response:
        body = json.loads(request.content)
        assert body["tools"] == tools
        return httpx.Response(
            200,
            json={
                "choices": [
                    {
                        "message": {
                            "role": "assistant",
                            "content": None,
                            "tool_calls": [
                                {
                                    "id": "call_1",
                                    "type": "function",
                                    "function": {
                                        "name": "search",
                                        "arguments": json.dumps({"keyword": "合同"}),
                                    },
                                }
                            ],
                        },
                        "finish_reason": "tool_calls",
                    }
                ]
            },
        )

    client = _mock_client(handler)
    resp = await client.chat([{"role": "user", "content": "找合同"}], tools=tools)
    assert resp is None  # 工具调用时 content 为空
    assert client.last_tool_calls[0]["function"]["name"] == "search"


@pytest.mark.asyncio
async def test_stream_delta():
    chunks = [
        {"choices": [{"delta": {"content": "你"}, "finish_reason": None}]},
        {"choices": [{"delta": {"content": "好"}, "finish_reason": None}]},
        {"choices": [{"delta": {"content": ""}, "finish_reason": "stop"}]},
    ]

    def handler(request: httpx.Request) -> httpx.Response:
        sse = "".join(f"data: {json.dumps(c, ensure_ascii=False)}\n\n" for c in chunks)
        sse += "data: [DONE]\n\n"
        return httpx.Response(200, headers={"content-type": "text/event-stream"}, content=sse)

    client = _mock_client(handler)
    got = []
    async for piece in client.stream([{"role": "user", "content": "hi"}]):
        got.append(piece)
    assert "".join(got) == "你好"
