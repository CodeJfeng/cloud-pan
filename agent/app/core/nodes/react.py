"""ReAct 路径节点：react_agent（决策）与 react_tools（执行工具）循环。

行为等价旧 Agent.stream_run，通过自定义事件（app.core.events.emit）保持
对外 SSE 契约：thinking / llm_decision / confirmation_required /
tool_start / tool_end / delta / done / error。

节点经图构造闭包注入 deps（react.ReactDeps），便于测试替换 model/tools。
"""
import logging
import time
import uuid
from typing import Any

from langchain_core.messages import (
    AIMessage,
    BaseMessage,
    HumanMessage,
    SystemMessage,
    ToolMessage,
)
from langgraph.types import interrupt

from app.core.context import (
    _looks_like_promised_action,
    _looks_like_unbacked_listing,
)
from app.core.events import emit
from app.core.prompts import (
    DIRECTORY_RULES,
    NUDGE_LISTING_MESSAGE,
    NUDGE_MESSAGE,
    NUDGE_SNAPSHOT_MESSAGE,
    SYSTEM_PROMPT,
)

logger = logging.getLogger(__name__)

# 承诺搜索兜底强制次数上限（等价旧 stream_run 的 continue_prompted < 2）
NUDGE_MAX = 2


class ReactDeps:
    """react 路径运行时依赖（节点经 graph 构造闭包注入，便于测试替换）。"""

    def __init__(self, model, tools_by_name: dict[str, Any], confirm: Any,
                 sensitive: frozenset[str] = frozenset(), interrupts: bool = True,
                 describe: Any = None):
        self.model = model
        self.tools_by_name = tools_by_name
        # async (tool_name, args, request_id) -> bool；None 表示不注入回调
        self.confirm = confirm
        # 敏感工具集合；触发确认的两种方式：confirm 回调（测试注入）或 interrupt（真实路径）
        self.sensitive = sensitive
        self.interrupts = interrupts
        # (tool_name, args) -> 可读描述 dict；None 表示不提供（审批弹窗回退原始参数）
        self.describe = describe


def describe_sensitive(
    describe: Any, name: str, args: dict[str, Any]
) -> dict[str, Any] | None:
    """生成审批用的可读描述（把加密 fileId 翻译成文件名）。

    描述器缺失或内部异常时返回 None，前端回退显示原始参数——
    审批是安全边界，宁可退化也不能因为翻译失败而漏掉确认。
    """
    if describe is None:
        return None
    try:
        return describe(name, args)
    except Exception:
        logger.warning("生成审批描述失败 | tool=%s", name, exc_info=True)
        return None


def _conversation(state: dict) -> list[BaseMessage]:
    """组装本次调模型的完整会话：系统提示词 + 目录快照 + 历史消息。

    系统提示词不入库（不持久化到 messages），每次调用时现算，保证切换
    用户/目录时提示词始终与本次请求一致。意图由模型自行判断（base.md 的
    【按情形处理】），不再按场景切换提示词。
    """
    system = SYSTEM_PROMPT
    ctx = state.get("directory_context")
    if ctx:
        # 目录感知规则 + 快照正文一并注入，让模型"先看见再行动"
        system = f"{system}\n\n{DIRECTORY_RULES}\n\n{ctx}"
    return [SystemMessage(content=system), *state["messages"]]


def _has_tool_result_this_turn(messages: list[BaseMessage]) -> bool:
    """本轮（最后一条用户消息之后）是否已有真实工具结果。"""
    for m in reversed(messages):
        if isinstance(m, ToolMessage):
            return True
        if isinstance(m, HumanMessage):
            return False
    return False


async def react_agent(state: dict, deps: "ReactDeps") -> dict:
    """调模型做一次决策并返回增量。

    有 tool_calls → 记录 llm_decision 并交 tools 节点；
    无工具调用：命中承诺搜索句式且未超限 → nudge（追加强制搜索的用户消息回环）；
    否则记录最终答案（final_answer）。
    """
    emit(type="thinking", content="正在分析你的需求…")
    t0 = time.perf_counter()
    resp: AIMessage = await deps.model.ainvoke(_conversation(state))
    llm_ms = int((time.perf_counter() - t0) * 1000)

    if resp.tool_calls:
        emit(
            type="llm_decision",
            tools=[c["name"] for c in resp.tool_calls],
            duration_ms=llm_ms,
        )
        return {"messages": [resp], "mode": "react"}

    answer = resp.content if isinstance(resp.content, str) else ""
    # 承诺"我来查看/再搜一下"却未调用工具 → nudge：
    # 有快照 → 引导直接基于快照回答；无快照 → 强制真正发起工具调用
    if (
        answer
        and _looks_like_promised_action(answer)
        and state.get("retries", 0) < NUDGE_MAX
    ):
        nudge_text = NUDGE_SNAPSHOT_MESSAGE if state.get("directory_context") else NUDGE_MESSAGE
        emit(type="llm_decision", tools=[], duration_ms=llm_ms, nudge=True)
        return {
            "messages": [resp, HumanMessage(content=nudge_text)],
            "retries": state.get("retries", 0) + 1,
        }

    # 无快照且无工具结果却宣称"已获取到目录内容" → 强制真正调用工具（防虚假工具调用）
    if (
        answer
        and _looks_like_unbacked_listing(answer)
        and not state.get("directory_context")
        and not _has_tool_result_this_turn(state["messages"])
        and state.get("retries", 0) < NUDGE_MAX
    ):
        emit(type="llm_decision", tools=[], duration_ms=llm_ms, nudge=True)
        return {
            "messages": [resp, HumanMessage(content=NUDGE_LISTING_MESSAGE)],
            "retries": state.get("retries", 0) + 1,
        }

    emit(type="llm_decision", tools=[], duration_ms=llm_ms)
    # 无工具调用即最终回答：等价旧 stream_run 的 delta（此处一次性）→ done
    if answer:
        emit(type="delta", content=answer)
    emit(type="done", content=answer)
    return {"messages": [resp], "final_answer": answer}


def _interrupt_confirm(
    name: str,
    args: dict[str, Any],
    display: dict[str, Any] | None = None,
) -> bool:
    """敏感工具确认（真实路径）：interrupt 挂起整图，runner 恢复后返回批复。

    挂在工具执行前调用——interrupt 前的节点副作用会被整体重放，因此
    react_tools 采用"先确认后执行"，保证恢复后工具只执行一次。

    `display` 是给用户看的可读描述（文件名/目标目录），与 `arguments`（工具真正
    使用的加密参数）并行传递：前者负责"让用户看懂在批什么"，后者保证执行不变。
    """
    request_id = uuid.uuid4().hex
    payload: dict[str, Any] = {"tool": name, "arguments": args, "requestId": request_id}
    if display is not None:
        payload["display"] = display
    approved = interrupt(payload)
    return bool(approved)


async def react_tools(state: dict, deps: "ReactDeps") -> dict:
    """执行最近一条 assistant 消息中的全部 tool_calls，结果回填为 ToolMessage。

    敏感工具采用"整批预确认再执行"：先对批内所有敏感调用取得批复，再统一
    执行——避免 interrupt 前已产生工具副作用、恢复重放导致重复执行。
    """
    msgs: list[BaseMessage] = []
    last_ai = next(
        (m for m in reversed(state["messages"]) if isinstance(m, AIMessage) and m.tool_calls),
        None,
    )
    if last_ai is None:
        return {"messages": msgs}
    calls = list(last_ai.tool_calls)

    # 预确认：敏感工具在真正执行前先获得批复（工具实际执行可能延后至最终恢复后）
    decisions: dict[str, bool] = {}
    for tc in calls:
        name = tc["name"]
        if name not in deps.sensitive:
            continue
        args = tc.get("args", {})
        display = describe_sensitive(deps.describe, name, args)
        if deps.confirm is not None:
            # 注入式确认（直接调用/单测路径）：由节点自行发出 confirmation_required
            request_id = uuid.uuid4().hex
            emit(
                type="confirmation_required",
                requestId=request_id,
                tool=name,
                arguments=args,
                display=display,
            )
            decisions[tc["id"]] = await deps.confirm(name, args, request_id)
        elif deps.interrupts:
            # 真实 interrupt 路径：confirmation_required 由 runner 根据中断值翻译
            decisions[tc["id"]] = _interrupt_confirm(name, args, display)
        else:
            decisions[tc["id"]] = True

    for tc in calls:
        name, args = tc["name"], tc.get("args", {})
        # 用户拒绝：不执行工具，以拒绝文本回填并标记 skipped
        if tc["id"] in decisions and not decisions[tc["id"]]:
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

        emit(
            type="tool_start",
            tool=name,
            arguments=args,
            display=describe_sensitive(deps.describe, name, args),
        )
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
            logger.exception("工具执行异常 | tool=%s | args=%s", name, args)
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
