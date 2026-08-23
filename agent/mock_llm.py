"""临时 mock LLM server：模拟 OpenAI 兼容 /v1/chat/completions（联调用，用完即删）。"""
import json

from fastapi import FastAPI, Request
from fastapi.responses import StreamingResponse

app = FastAPI()


def _sse(obj: dict) -> str:
    return f"data: {json.dumps(obj, ensure_ascii=False)}\n\n"


@app.post("/v1/chat/completions")
async def chat(req: Request):
    body = await req.json()
    stream = body.get("stream", False)
    messages = body.get("messages", [])
    has_tool_result = any(m.get("role") == "tool" for m in messages)

    if stream:
        # 流式：先发 tool 决策（无工具结果时），否则发最终文本增量
        if has_tool_result:
            chunks = [
                {"choices": [{"delta": {"content": "我"}, "finish_reason": None}]},
                {"choices": [{"delta": {"content": "找到"}, "finish_reason": None}]},
                {"choices": [{"delta": {"content": "3"}, "finish_reason": None}]},
                {"choices": [{"delta": {"content": "个文件"}, "finish_reason": None}]},
                {"choices": [{"delta": {}, "finish_reason": "stop"}]},
            ]
        else:
            chunks = [
                {"choices": [{"delta": {"content": "直"}, "finish_reason": None}]},
                {"choices": [{"delta": {"content": "接回答"}, "finish_reason": None}]},
                {"choices": [{"delta": {}, "finish_reason": "stop"}]},
            ]
        sse = "".join(_sse(c) for c in chunks) + "data: [DONE]\n\n"
        return StreamingResponse(
            iter([sse]), media_type="text/event-stream"
        )

    if has_tool_result:
        content = "我找到了相关文件，共 3 个。"
        return {
            "choices": [
                {
                    "message": {"role": "assistant", "content": content},
                    "finish_reason": "stop",
                }
            ]
        }

    tool_call = {
        "id": "call_mock_1",
        "type": "function",
        "function": {
            "name": "search_files",
            "arguments": json.dumps({"keyword": "合同"}, ensure_ascii=False),
        },
    }
    return {
        "choices": [
            {
                "message": {
                    "role": "assistant",
                    "content": None,
                    "tool_calls": [tool_call],
                },
                "finish_reason": "tool_calls",
            }
        ]
    }
