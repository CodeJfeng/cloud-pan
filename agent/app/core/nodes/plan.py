"""Plan-and-Execute 路径节点：router / planner / worker / collector / summarizer。

router（结构化输出判复杂度）→ planner（拆子任务）→ Send 并行扇出 N 个 worker
（各自受限工具集跑 mini ReAct）→ collector（聚合各子任务结果）→ summarizer
（流式产出最终回答 delta/done）。

worker 模型上下文为节点内局部会话：不写入全局 messages，避免并行分支互相
污染共享对话；collector 再把可读汇总作为上下文消息追加给 summarizer。
"""
from typing import Any, Literal

from langchain_core.messages import (
    AIMessage,
    BaseMessage,
    HumanMessage,
    SystemMessage,
    ToolMessage,
)
from langgraph.types import Send
from pydantic import BaseModel

from app.core.events import emit
from app.core.nodes.react import _interrupt_confirm
from app.core.prompts import (
    PLANNER_SYSTEM_PROMPT,
    SYSTEM_PROMPT,
    WORKER_SYSTEM_PROMPT,
)


class RouterDecision(BaseModel):
    """router 结构化输出：判定请求走 react 还是 plan。"""

    mode: Literal["react", "plan"]
    reason: str = ""


class PlanStep(BaseModel):
    """planner 拆出的单个子任务（自包含、无跨步依赖）。"""

    task_id: str
    description: str
    worker_group: Literal["search", "file_ops", "share", "recycle"]


class Plan(BaseModel):
    steps: list[PlanStep]


class PlanDeps:
    """plan 路径运行时依赖。"""

    def __init__(
        self,
        *,
        router_model: Any,
        planner_model: Any,
        worker_model: Any,
        summarizer_model: Any,
        tools_by_name: dict[str, Any],
        sensitive: frozenset[str],
        confirm: Any = None,
        worker_groups: dict[str, frozenset[str]] | None = None,
        worker_max_iterations: int = 3,
        interrupts: bool = True,
    ):
        self.router_model = router_model
        self.planner_model = planner_model
        self.worker_model = worker_model
        self.summarizer_model = summarizer_model
        self.tools_by_name = tools_by_name
        self.sensitive = sensitive
        self.confirm = confirm
        self.worker_groups = worker_groups or {}
        self.worker_max_iterations = worker_max_iterations
        self.interrupts = interrupts


async def router(state: dict, deps: "PlanDeps") -> dict:
    """判定复杂度：mode=plan 交给 planner，否则走 react；结构化输出失败兜底 react。"""
    try:
        decision: RouterDecision = await deps.router_model.ainvoke(state["messages"])
        mode = decision.mode if decision and decision.mode in ("react", "plan") else "react"
    except Exception:
        mode = "react"
    return {"mode": mode}


def route_after_router(state: dict) -> str:
    return "planner" if state.get("mode") == "plan" else "react_agent"


async def planner(state: dict, deps: "PlanDeps") -> dict:
    """把目标拆为相互独立的自包含子任务，存入 state.steps。"""
    conv = [SystemMessage(content=PLANNER_SYSTEM_PROMPT), *state["messages"]]
    plan: Plan = await deps.planner_model.ainvoke(conv)
    steps = [s.model_dump() for s in plan.steps]
    return {"steps": steps}


def dispatch(state: dict) -> list[Send]:
    """子任务 → Send 扇出：每个 step 启动一个独立 worker 并行执行。"""
    return [
        Send("worker", {"step": step, "user_input": state.get("user_input", "")})
        for step in state.get("steps", [])
    ]


def _worker_system(group: str) -> str:
    return f"{WORKER_SYSTEM_PROMPT}\n\n【本子任务类型：{group}】只能调用该类型允许的工具。"


async def worker(state: dict, deps: "PlanDeps") -> dict:
    """单个子任务：受限工具集内跑 mini ReAct（≤ worker_max_iterations 次）。

    会话上下文为节点局部变量（不写回全局 messages）；工具结果通过最终文本
    汇总，敏感工具同样 interrupt 确认。结束返回 results 归约项。
    """
    step = state["step"]
    group = step["worker_group"]
    allowed = deps.worker_groups.get(group) or set(deps.tools_by_name)
    conv: list[BaseMessage] = [
        SystemMessage(content=_worker_system(group)),
        HumanMessage(content=step["description"]),
    ]
    for _ in range(deps.worker_max_iterations):
        resp: AIMessage = await deps.worker_model.ainvoke(conv)
        if not resp.tool_calls:
            text = resp.content if isinstance(resp.content, str) else ""
            return {"results": [{"step_id": step["task_id"], "status": "ok", "result": text}]}
        conv.append(resp)
        calls = list(resp.tool_calls)
        # 预确认敏感调用，再统一执行（interrupt 前无副作用，恢复安全）
        decisions: dict[str, bool] = {}
        for tc in calls:
            if tc["name"] in deps.sensitive and deps.confirm is not None:
                decisions[tc["id"]] = await deps.confirm(tc["name"], tc.get("args", {}), "worker")
            elif tc["name"] in deps.sensitive and deps.interrupts:
                decisions[tc["id"]] = _interrupt_confirm(tc["name"], tc.get("args", {}))
        for tc in calls:
            name, args = tc["name"], tc.get("args", {})
            if tc["id"] in decisions and not decisions[tc["id"]]:
                conv.append(
                    ToolMessage(content="用户拒绝了该操作，未执行。", tool_call_id=tc["id"])
                )
                continue
            if name not in deps.tools_by_name or name not in allowed:
                text = f"工具 {name} 不在本子任务允许范围内，未执行。"
                conv.append(ToolMessage(content=text, tool_call_id=tc["id"]))
                continue
            try:
                result = await deps.tools_by_name[name].ainvoke(args)
                conv.append(ToolMessage(content=str(result), tool_call_id=tc["id"]))
            except Exception as exc:
                conv.append(
                    ToolMessage(content=f"工具执行异常: {exc}", tool_call_id=tc["id"])
                )
    return {
        "results": [
            {
                "step_id": step["task_id"],
                "status": "error",
                "result": "子任务超过最大执行轮次，未完成。",
            }
        ]
    }


async def collector(state: dict, deps: "PlanDeps") -> dict:
    """全部 worker 汇合后：把各子任务结果整理为给 summarizer 的汇总上下文。"""
    results = state.get("results", [])
    lines = [f"用户目标：{state.get('user_input') or ''}"]
    for r in results:
        mark = "成功" if r.get("status") == "ok" else f"失败（{r.get('status')}）"
        lines.append(f"- 子任务 {r.get('step_id')} {mark}：{r.get('result') or ''}")
    content = "以下是各子任务已完成的执行结果，请依据这些结果给用户最终答复：\n" + "\n".join(lines)
    return {"messages": [HumanMessage(content=content)]}


async def summarizer(state: dict, deps: "PlanDeps") -> dict:
    """对汇总上下文流式产出最终回答（emit delta），完整文本写入 final_answer。"""
    conv = [SystemMessage(content=SYSTEM_PROMPT), *state["messages"]]
    answer = ""
    async for chunk in deps.summarizer_model.astream(conv):
        piece = chunk if isinstance(chunk, str) else getattr(chunk, "content", "")
        if isinstance(piece, str) and piece:
            answer += piece
            emit(type="delta", content=piece)
    emit(type="done", content=answer)
    return {"final_answer": answer}
