"""LangGraph 状态图组装与编译。

完整拓扑（意图识别可用时）：
    START → intent ──chitchat→ chitchat（直答，本轮结束）→ END
                  ├─react───→ react_agent ⇄ react_tools（循环）→ END
                  ├─plan────→ planner → dispatch(Send×N) → worker
                  │                    → collector → summarizer → END
                  └─clarify─→ clarify（向用户追问，本轮结束）→ END

意图识别（nodes/intent.py）由 LLM 结构化输出判定意图与置信度；模型不支持
结构化输出时整图退化为纯 react（与测试替身/无能力模型保持兼容）。
model / mcp / checkpointer 由调用方注入（测试可替换）。
"""
from collections.abc import Awaitable, Callable
from typing import Any

from langgraph.graph import END, START, StateGraph
from langgraph.graph.state import CompiledStateGraph

from app.core.nodes import chitchat, intent, plan, react
from app.core.pan_tools import SENSITIVE_TOOLS, TOOL_GROUP, McpToolFactory
from app.core.state import AgentState


def _structured_capable(model: Any) -> bool:
    """模型是否具备结构化输出能力（真实聊天模型有 with_structured_output，测试替身没有）。

    仅作能力开关：实际调用走 app/core/structured.py 的 JSON 文本解析，不使用
    langchain 的 with_structured_output（DeepSeek 的 json_mode 要求 prompt 含
    "json" 字样，思考模式模型又禁止指定 tool_choice，两条路都会 400）。
    """
    return model is not None and hasattr(model, "with_structured_output")


def _bind_tools(model: Any, tools: dict[str, Any]) -> Any:
    """把 MCP 工具挂到模型上（只有执行侧需要工具 schema）。

    模型不支持 bind_tools（测试替身）或工具为空时原样返回，避免破坏既有替身行为。
    """
    if not tools or not hasattr(model, "bind_tools"):
        return model
    return model.bind_tools(list(tools.values()))


async def build_agent_graph(
    *,
    model: Any,
    mcp: Any,
    user_id: str,
    checkpointer: Any,
    confirm: Callable[[str, dict, str], Awaitable[bool]] | None = None,
    intent_model: Any | None = None,
    planner_model: Any | None = None,
    worker_model: Any | None = None,
    summarizer_model: Any | None = None,
    worker_max_iterations: int = 3,
) -> CompiledStateGraph:
    """组装整图并编译。

    未注入 intent_model 且 model 不具备结构化输出能力时退化为纯 react 图
    （模型替身保持既有行为）。

    模型分工（网关差异所致，见 app/core/structured.py）：
    - 执行侧（react_agent / worker）绑定工具 schema；
    - 意图识别 / planner / summarizer / 直答使用未绑定工具的模型。
    """
    factory = McpToolFactory(mcp, user_id)
    tools = {t.name: t for t in await factory.build_all()}
    # 执行侧模型：挂上工具 schema，否则模型无从产出 tool_calls
    react_model = _bind_tools(model, tools)
    react_deps = react.ReactDeps(
        model=react_model,
        tools_by_name=tools,
        confirm=confirm,
        sensitive=SENSITIVE_TOOLS,
    )

    # 意图识别：LLM 判定意图 + 置信度（可由外部注入专用模型）
    intent_m = intent_model or (model if _structured_capable(model) else None)
    # plan 路径：planner 同样依赖模型输出 JSON，能力缺失时 intent 的 plan 判定降级为 react
    planner_m = planner_model or (model if _structured_capable(model) else None)

    plan_deps: plan.PlanDeps | None = None
    if planner_m is not None:
        plan_deps = plan.PlanDeps(
            planner_model=planner_m,
            worker_model=worker_model or react_model,
            summarizer_model=summarizer_model or model,
            tools_by_name=tools,
            sensitive=SENSITIVE_TOOLS,
            confirm=confirm,
            worker_groups=TOOL_GROUP,
            worker_max_iterations=worker_max_iterations,
        )

    async def _react_agent(state: dict) -> dict:
        return await react.react_agent(state, react_deps)

    async def _react_tools(state: dict) -> dict:
        return await react.react_tools(state, react_deps)

    g = StateGraph(AgentState)
    g.add_node("react_agent", _react_agent)
    g.add_node("react_tools", _react_tools)
    g.add_conditional_edges(
        "react_agent",
        react.react_route,
        {"tools": "react_tools", "agent": "react_agent", "end": END},
    )
    g.add_edge("react_tools", "react_agent")

    if intent_m is not None:
        # 直答需要模型支持流式输出，否则 chitchat 判定降级为 react
        use_chitchat = hasattr(model, "astream")
        intent_deps = intent.IntentDeps(
            model=intent_m,
            allow_plan=plan_deps is not None,
            allow_chitchat=use_chitchat,
        )

        async def _intent(state: dict) -> dict:
            return await intent.intent(state, intent_deps)

        async def _clarify(state: dict) -> dict:
            return await intent.clarify(state)

        g.add_node("intent", _intent)
        g.add_node("clarify", _clarify)
        g.add_edge(START, "intent")
        branches: dict[str, str] = {"react_agent": "react_agent", "clarify": "clarify"}

        if use_chitchat:
            chitchat_deps = chitchat.ChitchatDeps(model=model)

            async def _chitchat(state: dict) -> dict:
                return await chitchat.chitchat(state, chitchat_deps)

            g.add_node("chitchat", _chitchat)
            g.add_edge("chitchat", END)
            branches["chitchat"] = "chitchat"

        if plan_deps is not None:
            branches["planner"] = "planner"

            async def _planner(state: dict) -> dict:
                return await plan.planner(state, plan_deps)

            async def _worker(state: dict) -> dict:
                return await plan.worker(state, plan_deps)

            async def _collector(state: dict) -> dict:
                return await plan.collector(state, plan_deps)

            async def _summarizer(state: dict) -> dict:
                return await plan.summarizer(state, plan_deps)

            g.add_node("planner", _planner)
            g.add_conditional_edges("planner", plan.dispatch, ["worker"])
            g.add_node("worker", _worker)
            g.add_edge("worker", "collector")
            g.add_node("collector", _collector)
            g.add_edge("collector", "summarizer")
            g.add_node("summarizer", _summarizer)
            g.add_edge("summarizer", END)
        g.add_conditional_edges("intent", intent.route_after_intent, branches)
    else:
        g.add_edge(START, "react_agent")

    return g.compile(checkpointer=checkpointer)
