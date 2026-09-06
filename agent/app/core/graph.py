"""LangGraph 状态图组装与编译。

完整拓扑（router 可用时）：
    START → router ──mode=react──→ react_agent ⇄ react_tools（循环）→ END
                  └─mode=plan──→ planner → dispatch(Send×N) → worker
                                  → collector → summarizer → END

router/planner 依赖模型 structured output；测试用替身或真实 ChatOpenAI
注入。model / mcp / checkpointer 由调用方注入（测试可替换）。
"""
from collections.abc import Awaitable, Callable
from typing import Any

from langgraph.graph import END, START, StateGraph
from langgraph.graph.state import CompiledStateGraph

from app.core.nodes import plan, react
from app.core.pan_tools import SENSITIVE_TOOLS, TOOL_GROUP, McpToolFactory
from app.core.state import AgentState


def _with_structured(model: Any, schema: type) -> Any | None:
    """由聊天模型派生结构化输出模型；不支持的模型返回 None。"""
    if model is None or not hasattr(model, "with_structured_output"):
        return None
    try:
        return model.with_structured_output(schema, method="json_mode")
    except TypeError:
        return model.with_structured_output(schema)


async def build_agent_graph(
    *,
    model: Any,
    mcp: Any,
    user_id: str,
    checkpointer: Any,
    confirm: Callable[[str, dict, str], Awaitable[bool]] | None = None,
    router_model: Any | None = None,
    planner_model: Any | None = None,
    worker_model: Any | None = None,
    summarizer_model: Any | None = None,
    worker_max_iterations: int = 3,
) -> CompiledStateGraph:
    """组装整图并编译。

    未注入 router/planner 且 model 不支持 structured output 时退化为
    纯 react 图（迁移期保持既有 react 测试/API 兼容）。
    """
    factory = McpToolFactory(mcp, user_id)
    tools = {t.name: t for t in await factory.build_all()}
    react_deps = react.ReactDeps(
        model=model,
        tools_by_name=tools,
        confirm=confirm,
        sensitive=SENSITIVE_TOOLS,
    )

    use_plan = router_model is not None or _with_structured(model, plan.RouterDecision) is not None
    if use_plan:
        router_m = router_model or _with_structured(model, plan.RouterDecision)
        planner_m = planner_model or _with_structured(model, plan.Plan)
        if router_m is None or planner_m is None:
            use_plan = False

    plan_deps: plan.PlanDeps | None = None
    if use_plan:
        plan_deps = plan.PlanDeps(
            router_model=router_m,
            planner_model=planner_m,
            worker_model=worker_model or model,
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

    if use_plan:
        async def _router(state: dict) -> dict:
            return await plan.router(state, plan_deps)

        async def _planner(state: dict) -> dict:
            return await plan.planner(state, plan_deps)

        async def _worker(state: dict) -> dict:
            return await plan.worker(state, plan_deps)

        async def _collector(state: dict) -> dict:
            return await plan.collector(state, plan_deps)

        async def _summarizer(state: dict) -> dict:
            return await plan.summarizer(state, plan_deps)

        g.add_node("router", _router)
        g.add_edge(START, "router")
        g.add_conditional_edges(
            "router",
            plan.route_after_router,
            {"planner": "planner", "react_agent": "react_agent"},
        )
        g.add_node("planner", _planner)
        g.add_conditional_edges("planner", plan.dispatch, ["worker"])
        g.add_node("worker", _worker)
        g.add_edge("worker", "collector")
        g.add_node("collector", _collector)
        g.add_edge("collector", "summarizer")
        g.add_node("summarizer", _summarizer)
        g.add_edge("summarizer", END)
    else:
        g.add_edge(START, "react_agent")

    return g.compile(checkpointer=checkpointer)
