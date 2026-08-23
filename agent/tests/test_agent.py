import json
from types import SimpleNamespace

import pytest

from app.core.agent import Agent, classify_intent
from app.core.llm import LLMClient
from app.core.prompts import SCENARIO_PROMPTS
from app.mcp.client import McpToolInfo

MCP_TOOLS = [
    McpToolInfo(
        name="list_files",
        description="列出文件夹内容",
        input_schema={
            "type": "object",
            "properties": {
                "userId": {"type": "string"},
                "parentId": {"type": "string"},
            },
            "required": ["userId", "parentId"],
        },
    )
]


class FakeMCP:
    def __init__(self):
        self.calls = []

    async def connect(self):
        pass

    async def close(self):
        pass

    async def list_tools(self):
        return MCP_TOOLS

    async def call_tool(self, name, arguments, user_id):
        self.calls.append((name, arguments, user_id))
        return f"{name} 结果: parentId={arguments.get('parentId')}"


class FakeLLM:
    """按顺序返回预设响应：首次 tool_call，随后最终文本。"""

    def __init__(self, script):
        self._script = list(script)
        self.last_tool_calls = []
        self.calls = []
        self._last_content = ""

    async def chat(self, messages, tools=None, temperature=0.7):
        self.calls.append({"messages": messages, "tools": tools})
        step = self._script.pop(0)
        if step.get("tool_calls"):
            self.last_tool_calls = step["tool_calls"]
            self._last_content = ""
            return None
        self.last_tool_calls = []
        self._last_content = step.get("content") or ""
        return self._last_content

    async def stream(self, messages, tools=None, temperature=0.7):
        if self._last_content:
            yield self._last_content


def make_agent():
    mcp = FakeMCP()
    llm = FakeLLM(
        [
            {
                "tool_calls": [
                    {
                        "id": "call_1",
                        "type": "function",
                        "function": {
                            "name": "list_files",
                            "arguments": json.dumps({"parentId": "abc"}),
                        },
                    }
                ]
            },
            {"content": "你的文件夹中有 3 个文件。"},
        ]
    )
    return Agent(llm=llm, mcp=mcp, user_id="42")


@pytest.mark.asyncio
async def test_simple_answer_without_tool():
    llm = FakeLLM([{"content": "你好，有什么可以帮你？"}])
    agent = Agent(llm=llm, mcp=FakeMCP(), user_id="42")
    answer = await agent.run("你好")
    assert answer == "你好，有什么可以帮你？"
    # 工具 schema 应已传给 LLM 且剥离了 userId
    tools = llm.calls[0]["tools"]
    assert tools[0]["function"]["name"] == "list_files"
    assert "userId" not in tools[0]["function"]["parameters"]["properties"]


@pytest.mark.asyncio
async def test_tool_call_loop():
    agent = make_agent()
    answer = await agent.run("帮我看看 abc 文件夹里有什么")
    assert answer == "你的文件夹中有 3 个文件。"
    # 工具被调用且注入 userId
    assert agent.mcp.calls == [("list_files", {"parentId": "abc"}, "42")]
    # LLM 收到 tool 结果消息
    tool_msg = [
        m for m in llm_tool_messages(agent.llm)
    ]
    assert any("parentId=abc" in m["content"] for m in tool_msg)


def llm_tool_messages(llm):
    for call in llm.calls:
        for m in call["messages"]:
            if m.get("role") == "tool":
                yield m


@pytest.mark.asyncio
async def test_stream_run_nudges_promised_search():
    """模型承诺继续搜索却未发起工具调用时，stream_run 会强制其继续调用工具后再结束。"""
    llm = FakeLLM(
        [
            {
                "tool_calls": [
                    {
                        "id": "call_1",
                        "type": "function",
                        "function": {
                            "name": "search_files",
                            "arguments": json.dumps({"keyword": "李京峰-个人简历"}),
                        },
                    }
                ]
            },
            {"content": "没有找到完全匹配，让我再用关键词「李京峰」搜一下"},
            {
                "tool_calls": [
                    {
                        "id": "call_2",
                        "type": "function",
                        "function": {
                            "name": "search_files",
                            "arguments": json.dumps({"keyword": "李京峰"}),
                        },
                    }
                ]
            },
            {"content": "仍未找到相关文件，请确认文件是否已上传。"},
        ]
    )
    agent = Agent(llm=llm, mcp=FakeMCP(), user_id="42")
    events = [ev async for ev in agent.stream_run("查找“李京峰-个人简历”的文件")]

    # 出现 nudge 事件：强制继续搜索
    assert any(ev.get("type") == "llm_decision" and ev.get("nudge") for ev in events)
    # 两次搜索工具调用都真实发生
    assert [c[0] for c in agent.mcp.calls] == ["search_files", "search_files"]
    # 最终给出结论而非中途结束
    assert events[-1]["type"] == "done"
    assert "仍未找到" in events[-1]["content"]


@pytest.mark.asyncio
async def test_stream_run_final_answer_not_nudged():
    """正常最终回答（不含'再搜一下'承诺）不应被 nudge，直接结束。"""
    llm = FakeLLM(
        [
            {
                "tool_calls": [
                    {
                        "id": "call_1",
                        "type": "function",
                        "function": {
                            "name": "search_files",
                            "arguments": json.dumps({"keyword": "李京峰"}),
                        },
                    }
                ]
            },
            {"content": "找到了文件：李京峰-个人简历.pdf"},
        ]
    )
    agent = Agent(llm=llm, mcp=FakeMCP(), user_id="42")
    events = [ev async for ev in agent.stream_run("找李京峰简历")]
    assert not any(ev.get("nudge") for ev in events)
    assert events[-1]["type"] == "done"
    assert "找到了文件" in events[-1]["content"]


@pytest.mark.parametrize(
    "message,expected",
    [
        ("帮我搜索李京峰简历", "search"),
        ("查找“李京峰-个人简历”的文件", "search"),
        ("有没有叫合同的文件", "search"),
        ("把这个文件夹分享给同事", "share"),
        ("创建分享链接", "share"),
        ("新建一个文件夹", "file_ops"),
        ("删除这个文件", "file_ops"),
        ("把文件移动到项目目录", "file_ops"),
        ("你好，今天天气不错", "default"),
    ],
)
def test_classify_intent(message, expected):
    assert classify_intent(message) == expected


def test_scenario_prompts_all_scenarios_present():
    for key in ("search", "file_ops", "share", "default"):
        assert key in SCENARIO_PROMPTS
        assert SCENARIO_PROMPTS[key].strip()


def _system_prompt(llm):
    return llm.calls[0]["messages"][0]["content"]


@pytest.mark.asyncio
async def test_run_loads_search_prompt_by_intent():
    """run：搜索消息自动加载搜索场景提示词。"""
    llm = FakeLLM([{"content": "正在搜索。"}])
    agent = Agent(llm=llm, mcp=FakeMCP(), user_id="42")
    await agent.run("查找“李京峰-个人简历”的文件")
    assert "【当前场景：文件搜索】" in _system_prompt(llm)
    assert "至少尝试 2 次不同关键词" in _system_prompt(llm)


@pytest.mark.asyncio
async def test_run_loads_file_ops_prompt_by_intent():
    llm = FakeLLM([{"content": "好的，先确认下。"}])
    agent = Agent(llm=llm, mcp=FakeMCP(), user_id="42")
    await agent.run("删除这个文件")
    assert "【当前场景：文件操作】" in _system_prompt(llm)


@pytest.mark.asyncio
async def test_run_prompt_key_overrides_intent():
    """run：显式 prompt_key 优先于意图路由。"""
    llm = FakeLLM([{"content": "好的。"}])
    agent = Agent(llm=llm, mcp=FakeMCP(), user_id="42")
    await agent.run("你好", prompt_key="share")
    assert "【当前场景：创建分享】" in _system_prompt(llm)


@pytest.mark.asyncio
async def test_stream_run_loads_search_prompt_by_intent():
    """stream_run：搜索消息自动加载搜索场景提示词。"""
    llm = FakeLLM([{"content": "好的，搜索中。"}])
    agent = Agent(llm=llm, mcp=FakeMCP(), user_id="42")
    events = [ev async for ev in agent.stream_run("搜索李京峰简历")]
    assert events[-1]["type"] == "done"
    assert "【当前场景：文件搜索】" in _system_prompt(llm)


@pytest.mark.asyncio
async def test_tool_error_is_fed_back():
    class ErrMCP(FakeMCP):
        async def call_tool(self, name, arguments, user_id):
            raise RuntimeError("boom")

    llm = FakeLLM(
        [
            {
                "tool_calls": [
                    {
                        "id": "call_1",
                        "type": "function",
                        "function": {"name": "list_files", "arguments": "{}"},
                    }
                ]
            },
            {"content": "抱歉，执行出错。"},
        ]
    )
    agent = Agent(llm=llm, mcp=ErrMCP(), user_id="42")
    answer = await agent.run("查文件")
    assert answer == "抱歉，执行出错。"
