"""ReAct 路径节点：react_agent（决策）与 react_tools（执行工具）循环。

行为等价旧 Agent.stream_run，通过自定义事件（app.core.events.emit）保持
对外 SSE 契约：thinking / llm_decision / confirmation_required /
tool_start / tool_end / delta / done / error。

节点经图构造闭包注入 deps（react.ReactDeps），便于测试替换 model/tools。
"""
import time
import uuid
from typing import Any

from langchain_core.messages import AIMessage, BaseMessage, HumanMessage, ToolMessage

from app.core.context import _looks_like_promised_search
from app.core.events import emit

# nudge：模型口头承诺继续却未发起工具调用时注入的强制指令（等价旧行为）
NUDGE_MESSAGE = (
    "你刚才说还要继续搜索/检查。请直接调用搜索工具完成并如实汇报结果，"
    "不要只描述意图而不调用工具。"
)

# 承诺搜索兜底强制次数上限（等价旧 stream_run 的 continue_prompted < 2）
NUDGE_MAX = 2


class ReactDeps:
    """react 路径运行时依赖（节点经 graph 构造闭包注入，便于测试替换）。"""

    def __init__(self, model, tools_by_name: dict[str, Any], confirm: Any,
                 sensitive: frozenset[str] = frozenset()):
        self.model = model
        self.tools_by_name = tools_by_name
        self.confirm = confirm  # async (tool_name, args, request_id) -> bool；None 表示不确认直接执行
        self.sensitive = sensitive


async def react_agent(state: dict, deps: "ReactDeps") -> dict:
    """调模型做一次决策并返回增量。

    有 tool_calls → 记录 llm_decision 并交 tools 节点；
    无工具调用：命中承诺搜索句式且未超限 → nudge（追加强制搜索的用户消息回环）；
    否则记录最终答案（final_answer）。
    """
    emit(type="thinking", content="正在分析你的需求…")
    t0 = time.perf_counter()
    resp: AIMessage = await deps.model.ainvoke(state["messages"])
    llm_ms = int((time.perf_counter() - t0) * 1000)

    if resp.tool_calls:
        emit(
            type="llm_decision",
            tools=[c["name"] for c in resp.tool_calls],
            duration_ms=llm_ms,
        )
        return {"messages": [resp], "mode": "react"}

    answer = resp.content if isinstance(resp.content, str) else ""
    # 承诺继续搜索却未调用工具 → 强制其真正发起搜索（nudge）
    if (
        answer
        and _looks_like_promised_search(answer)
        and state.get("retries", 0) < NUDGE_MAX
    ):
        emit(type="llm_decision", tools=[], duration_ms=llm_ms, nudge=True)
        return {
            "messages": [resp, HumanMessage(content=NUDGE_MESSAGE)],
            "retries": state.get("retries", 0) + 1,
        }

    emit(type="llm_decision", tools=[], duration_ms=llm_ms)
    return {"messages": [resp], "final_answer": answer}


async def react_tools(state: dict, deps: "ReactDeps") -> dict:
    """执行最近一条 assistant 消息中的全部 tool_calls，结果回填为 ToolMessage。"""
    msgs: list[BaseMessage] = []
    last_ai = next(
        (m for m in reversed(state["messages"]) if isinstance(m, AIMessage) and m.tool_calls),
        None,
    )
    if last_ai is None:
        return {"messages": msgs}
    for tc in last_ai.tool_calls:
        name, args = tc["name"], tc.get("args", {})
        # 敏感工具确认（任务 8 起经 interrupt 接入；本实现兼容可注入确认器回调）
        if name in deps.sensitive and deps.confirm is not None:
            request_id = uuid.uuid4().hex
            emit(
                type="confirmation_required",
                requestId=request_id,
                tool=name,
                arguments=args,
            )
            approved = await deps.confirm(name, args, request_id)
            if not approved:
                result = "用户拒绝了该操作，未执行。"
                msgs.append(ToolMessage(content=result, tool_call_id=tc["id"]))
                emit(
                    type="tool_end",
                    tool=name,
                    result=result,
                    duration_ms=0,
                    skipped=True,
                )
                continue

        emit(type="tool_start", tool=name, arguments=args)
        t0 = time.perf_counter()
        try:
            result = await deps.tools_by_name[name].ainvoke(args)
            msgs.append(ToolMessage(content=str(result), tool_call_id=tc["id"]))
            emit(
                type="tool_end",
                tool=name,
                result=str(result),
                duration_ms=int((time.perf_counter() - t0) * 1000),
            )
        except Exception as exc:
            text = f"工具执行异常: {exc}"
            msgs.append(ToolMessage(content=text, tool_call_id=tc["id"]))
            emit(
                type="tool_end",
                tool=name,
                result=text,
                duration_ms=int((time.perf_counter() - t0) * 1000),
                error=True,
            )
    return {"messages": msgs}


def react_route(state: dict) -> str:
    """react_agent 之后的条件路由。

    - "tools": 最近一条 assistant 消息带 tool_calls，去执行工具；
    - "agent": 最近一条是 nudge 注入的用户消息，回到 agent 再决策；
    - "end": 已产出最终答案。
    """
    last = state["messages"][-1]
    if isinstance(last, AIMessage) and last.tool_calls:
        return "tools"
    if isinstance(last, HumanMessage):
        return "agent"
    return "end"
