import pytest
from langchain_core.messages import AIMessage, HumanMessage

from app.core.checkpoint import build_checkpointer
from app.core.graph import build_agent_graph
from app.core.nodes import intent as intent_node
from app.core.nodes.intent import (
    CONFIDENCE_THRESHOLD,
    IntentDecision,
    IntentDeps,
)
from app.core.prompts import CLARIFY_MESSAGE, INTENT_SYSTEM_PROMPT
from app.mcp.client import McpToolInfo
from tests.helpers import ScriptedModel, StubStructured

MCP_TOOLS = [
    McpToolInfo(
        name="search_files",
        description="搜索文件",
        input_schema={
            "type": "object",
            "properties": {"keyword": {"type": "string"}},
            "required": ["keyword"],
        },
    )
]


class FakeMCP:
    async def connect(self):
        pass

    async def close(self):
        pass

    async def list_tools(self):
        return MCP_TOOLS

    async def call_tool(self, name, arguments, user_id):
        return "搜索结果"


def _state(text: str = "帮我找一下合同") -> dict:
    return {"messages": [HumanMessage(content=text)], "user_input": text}


@pytest.mark.asyncio
async def test_intent_high_confidence_routes_react():
    """高置信度且单步：mode=react，intent 与 confidence 落 state。"""
    model = StubStructured(
        [IntentDecision(intent="search", confidence=0.95, mode="react", reason="单步搜索")]
    )
    out = await intent_node.intent(_state(), IntentDeps(model=model))
    assert out["mode"] == "react"
    assert out["intent"] == "search"
    assert out["confidence"] == 0.95
    # 意图判定注入了专属 system 提示词
    sent = model.calls[0]
    assert sent[0].content == INTENT_SYSTEM_PROMPT
    assert intent_node.route_after_intent(out) == "react_agent"


@pytest.mark.asyncio
async def test_intent_multi_task_routes_plan():
    """多步可并行：mode=plan（planner 可用时）。"""
    model = StubStructured(
        [IntentDecision(intent="file_ops", confidence=0.9, mode="plan", reason="两个独立操作")]
    )
    out = await intent_node.intent(_state("建个文件夹再搜合同"), IntentDeps(model=model))
    assert out["mode"] == "plan"
    assert intent_node.route_after_intent(out) == "planner"


@pytest.mark.asyncio
async def test_intent_plan_degrades_to_react_without_planner():
    """planner 不可用时（模型不支持结构化输出）plan 判定降级为 react。"""
    model = StubStructured([IntentDecision(intent="file_ops", confidence=0.9, mode="plan")])
    out = await intent_node.intent(_state(), IntentDeps(model=model, allow_plan=False))
    assert out["mode"] == "react"


@pytest.mark.asyncio
async def test_low_confidence_uses_model_clarify_question():
    """置信度低于阈值：走澄清分支，追问用模型给出的话术。"""
    model = StubStructured(
        [
            IntentDecision(
                intent="file_ops",
                confidence=CONFIDENCE_THRESHOLD - 0.1,
                mode="react",
                clarify="你想删除哪个文件？告诉我文件名或所在目录。",
            )
        ]
    )
    out = await intent_node.intent(_state("把它删了"), IntentDeps(model=model))
    assert out["mode"] == "clarify"
    assert out["intent"] == "unclear"
    assert out["clarify_question"] == "你想删除哪个文件？告诉我文件名或所在目录。"
    assert intent_node.route_after_intent(out) == "clarify"


@pytest.mark.asyncio
async def test_unclear_intent_asks_back():
    """模型判为 unclear：走澄清，追问缺省时用兜底文案。"""
    model = StubStructured([IntentDecision(intent="unclear", confidence=0.9, mode="react")])
    out = await intent_node.intent(_state("嗯……"), IntentDeps(model=model))
    assert out["mode"] == "clarify"
    assert out["clarify_question"] == CLARIFY_MESSAGE


@pytest.mark.asyncio
async def test_unparsable_output_asks_back():
    """模型没按 JSON 格式答（输出无法解析）：追问，而不是硬猜。"""
    model = ScriptedModel([AIMessage(content="我猜你是想找文件吧")])
    out = await intent_node.intent(_state("弄一下那个"), IntentDeps(model=model))
    assert out["mode"] == "clarify"
    assert out["intent"] == "unclear"


@pytest.mark.asyncio
async def test_gateway_failure_degrades_to_react():
    """网关/网络故障：降级 react，不伪装成"意图不明"让用户重新描述需求。"""

    class Boom:
        async def ainvoke(self, messages):
            raise RuntimeError("llm down")

    out = await intent_node.intent(_state("你是谁"), IntentDeps(model=Boom()))
    assert out["mode"] == "react"
    assert out["intent"] == "unknown"


@pytest.mark.asyncio
async def test_clarify_node_emits_delta_and_done():
    """澄清节点复用既有 SSE 契约：delta + done，内容即追问。"""
    events: list[dict] = []

    original = intent_node.emit
    intent_node.emit = lambda **kw: events.append(kw)
    try:
        out = await intent_node.clarify({"clarify_question": "请问要删除哪个文件？"})
    finally:
        intent_node.emit = original

    assert [e["type"] for e in events] == ["delta", "done"]
    assert all(e["content"] == "请问要删除哪个文件？" for e in events)
    assert out["final_answer"] == "请问要删除哪个文件？"


@pytest.mark.asyncio
async def test_graph_clarify_branch_ends_with_question():
    """整图：低置信度 → clarify 分支结束，不调用任何工具、不再走 react。"""
    intent_model = StubStructured(
        [IntentDecision(intent="unclear", confidence=0.3, mode="react", clarify="要我帮你找哪个文件？")]
    )
    model = ScriptedModel([AIMessage(content="不该被调用")])
    graph = await build_agent_graph(
        model=model,
        mcp=FakeMCP(),
        user_id="42",
        checkpointer=build_checkpointer(force_memory=True),
        intent_model=intent_model,
    )
    out = await graph.ainvoke(
        {"messages": [HumanMessage(content="弄一下那个")], "user_input": "弄一下那个"},
        {"configurable": {"thread_id": "c1"}},
    )
    assert out["mode"] == "clarify"
    assert out["final_answer"] == "要我帮你找哪个文件？"
    assert model.calls == []


@pytest.mark.asyncio
async def test_runner_streams_clarify_as_done():
    """SSE 契约：澄清经 runner 输出 delta + done，前端无需新增事件类型。"""
    from app.core import runner

    intent_model = StubStructured(
        [IntentDecision(intent="unclear", confidence=0.2, mode="react", clarify="要搜索哪个文件？")]
    )
    graph = await build_agent_graph(
        model=ScriptedModel([]),
        mcp=FakeMCP(),
        user_id="42",
        checkpointer=build_checkpointer(force_memory=True),
        intent_model=intent_model,
    )
    events = [
        e
        async for e in runner.run_agent_stream(
            graph=graph,
            inputs={"messages": [HumanMessage(content="找一下")], "user_input": "找一下"},
            config={"configurable": {"thread_id": "c3"}},
            user_id="42",
        )
    ]
    assert [(e["type"], e.get("content")) for e in events] == [
        ("delta", "要搜索哪个文件？"),
        ("done", "要搜索哪个文件？"),
    ]


@pytest.mark.asyncio
async def test_graph_intent_react_then_answer():
    """整图：高置信度且需要工具 → react 正常回答。"""
    intent_model = StubStructured(
        [IntentDecision(intent="search", confidence=0.93, mode="react")]
    )
    model = ScriptedModel([AIMessage(content="已找到合同文件")])
    graph = await build_agent_graph(
        model=model,
        mcp=FakeMCP(),
        user_id="42",
        checkpointer=build_checkpointer(force_memory=True),
        intent_model=intent_model,
    )
    out = await graph.ainvoke(
        {"messages": [HumanMessage(content="帮我找合同")], "user_input": "帮我找合同"},
        {"configurable": {"thread_id": "c2"}},
    )
    assert out["intent"] == "search"
    assert out["mode"] == "react"
    assert out["final_answer"] == "已找到合同文件"


@pytest.mark.asyncio
async def test_intent_chitchat_routes_to_direct_answer():
    """chitchat 意图 → 直答分支；模型不支持流式时降级 react。"""
    model = StubStructured(
        [IntentDecision(intent="chitchat", confidence=0.99, mode="react")]
    )
    out = await intent_node.intent(_state("你是谁"), IntentDeps(model=model))
    assert out["mode"] == "chitchat"
    assert intent_node.route_after_intent(out) == "chitchat"

    model2 = StubStructured(
        [IntentDecision(intent="chitchat", confidence=0.99, mode="react")]
    )
    out2 = await intent_node.intent(
        _state("你是谁"), IntentDeps(model=model2, allow_chitchat=False)
    )
    assert out2["mode"] == "react"


@pytest.mark.asyncio
async def test_chitchat_node_streams_without_snapshot():
    """直答节点：流式 delta + done，且不注入目录快照与 base 工具强约束。"""
    from app.core.nodes import chitchat as chitchat_node
    from app.core.prompts import CHITCHAT_SYSTEM_PROMPT, SYSTEM_PROMPT

    events: list[dict] = []
    original = chitchat_node.emit
    chitchat_node.emit = lambda **kw: events.append(kw)
    try:
        model = ScriptedModel([AIMessage(content="我是你的云盘助手，可以帮你找文件。")])
        out = await chitchat_node.chitchat(
            {
                "messages": [HumanMessage(content="你是谁")],
                "directory_context": "【当前目录】根目录（内部目录ID: -1）",
            },
            chitchat_node.ChitchatDeps(model=model),
        )
    finally:
        chitchat_node.emit = original

    assert [e["type"] for e in events] == ["delta", "done"]
    assert out["final_answer"] == "我是你的云盘助手，可以帮你找文件。"
    sent = model.calls[0]
    assert sent[0].content == CHITCHAT_SYSTEM_PROMPT
    assert sent[0].content != SYSTEM_PROMPT
    assert "当前目录" not in sent[0].content


@pytest.mark.asyncio
async def test_graph_chitchat_branch_never_calls_react():
    """整图：闲聊走直答，react 不被调用，目录快照不进提示词。"""
    intent_model = StubStructured(
        [IntentDecision(intent="chitchat", confidence=0.99, mode="react")]
    )
    model = ScriptedModel([AIMessage(content="我是云盘助手，能帮你搜索、整理和分享文件。")])
    graph = await build_agent_graph(
        model=model,
        mcp=FakeMCP(),
        user_id="42",
        checkpointer=build_checkpointer(force_memory=True),
        intent_model=intent_model,
    )
    out = await graph.ainvoke(
        {
            "messages": [HumanMessage(content="你能做什么")],
            "user_input": "你能做什么",
            "directory_context": "【当前目录】根目录（内部目录ID: -1）",
        },
        {"configurable": {"thread_id": "c5"}},
    )
    assert out["mode"] == "chitchat"
    assert out["final_answer"].startswith("我是云盘助手")
    # 只调用了一次模型（直答），且提示词里没有目录快照
    assert len(model.calls) == 1
    assert "根目录" not in model.calls[0][0].content
