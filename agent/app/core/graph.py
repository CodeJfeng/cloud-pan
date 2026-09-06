"""LangGraph 状态图组装与编译。

react 路径先行（任务 6/7）；router / planner / worker / collector / summarizer
于任务 9 挂入。model / mcp / checkpointer 由调用方注入（测试可替换）。
"""
from collections.abc import Awaitable, Callable
from typing import Any

from langgraph.graph import END, START, StateGraph
from langgraph.graph.state import CompiledStateGraph

from app.core.nodes import react
from app.core.pan_tools import SENSITIVE_TOOLS, McpToolFactory
from app.core.state import AgentState


async def build_agent_graph(
    *,
    model: Any,
    mcp: Any,
    user_id: str,
    checkpointer: Any,
    confirm: Callable[[str, dict, str], Awaitable[bool]] | None = None,
) -> CompiledStateGraph:
    """组装整图并编译。react 路径先行，后续任务挂 router/plan（接口不变）。

    model/mcp 由调用方注入；MCP 工具清单经 async 拉取后转换为
    绑定 user_id 的 LangChain 工具（schema 不含 userId）。
    """
    factory = McpToolFactory(mcp, user_id)
    tools = {t.name: t for t in await factory.build_all()}
    deps = react.ReactDeps(
        model=model,
        tools_by_name=tools,
        confirm=confirm,
        sensitive=SENSITIVE_TOOLS,
    )

    async def _react_agent(state: dict) -> dict:
        return await react.react_agent(state, deps)

    async def _react_tools(state: dict) -> dict:
        return await react.react_tools(state, deps)

    g = StateGraph(AgentState)
    g.add_node("react_agent", _react_agent)
    g.add_node("react_tools", _react_tools)
    g.add_edge(START, "react_agent")
    g.add_conditional_edges(
        "react_agent",
        react.react_route,
        {"tools": "react_tools", "agent": "react_agent", "end": END},
    )
    g.add_edge("react_tools", "react_agent")
    return g.compile(checkpointer=checkpointer)
