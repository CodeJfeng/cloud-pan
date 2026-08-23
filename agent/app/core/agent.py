"""云盘 Agent 核心循环：动态拉取 MCP 工具 → LLM 决策 → 工具执行 → 结果回填。"""
import json
import re
import time
import uuid
from collections.abc import Awaitable, Callable
from typing import Any, AsyncIterator

from app.core.llm import LLMClient
from app.core.prompts import SCENARIO_PROMPTS, SYSTEM_PROMPT
from app.mcp.client import MCPClient, MCPClientError

MAX_TOOL_TURNS = 8

# 敏感工具：执行前必须获得用户批准（移动/删除类操作，不可撤销或影响面大）
SENSITIVE_TOOLS = frozenset({"transfer_files", "delete_file"})

# 意图路由关键词表：按用户消息命中场景，实现提示词按需加载。
# 顺序即优先级——"分享"是最强信号，其次是搜索，最后是文件操作。
_INTENT_KEYWORDS: dict[str, tuple[str, ...]] = {
    "share": ("分享", "共享", "提取码", "访问码"),
    "search": (
        "搜索", "查找", "查一下", "查询", "找一下", "找到", "搜一下",
        "搜到", "有没有", "在哪", "在哪里", "找出",
    ),
    "file_ops": (
        "新建", "创建", "重命名", "改名", "删除", "删掉", "移除",
        "移动", "移到", "复制", "拷贝", "还原", "恢复", "回收站",
    ),
}


def classify_intent(message: str) -> str:
    """按用户消息关键词路由到场景（share / search / file_ops / default）。"""
    for intent, keywords in _INTENT_KEYWORDS.items():
        if any(k in message for k in keywords):
            return intent
    return "default"


def resolve_system_prompt(prompt_key: str | None, user_message: str) -> str:
    """按需加载系统提示词：优先使用调用方指定的场景，否则按消息意图自动路由。"""
    key = prompt_key or classify_intent(user_message)
    return SCENARIO_PROMPTS.get(key, SYSTEM_PROMPT)

# 模型"口头承诺继续搜索/检查"却未发起工具调用的常见句式，命中后强制其继续调用工具
_PROMISED_CONTINUE_PATTERN = re.compile(
    r"(?:让我再|我再|继续搜索|继续查找|再搜索|再查一下|再找找|再试(?:一次|试)?|"
    r"再尝试|换个关键词|换个关键字|换关键词|换种方式|换个词|用其他|变体)"
)


def _looks_like_promised_search(answer: str) -> bool:
    return bool(_PROMISED_CONTINUE_PATTERN.search(answer))


class Agent:
    def __init__(self, llm: LLMClient, mcp: MCPClient, user_id: str) -> None:
        self.llm = llm
        self.mcp = mcp
        self.user_id = user_id
        self._tools: list[Any] | None = None
        self._openai_tools: list[dict[str, Any]] = []

    async def _ensure_tools(self) -> None:
        if self._tools is None:
            await self.mcp.connect()
            self._tools = await self.mcp.list_tools()
            self._openai_tools = MCPClient.to_openai_tools(self._tools)

    async def run(
        self,
        user_message: str,
        history: list[dict[str, Any]] | None = None,
        prompt_key: str | None = None,
    ) -> str:
        """执行一轮 Agent 对话，返回最终回答文本。

        prompt_key: 指定提示词场景（search / file_ops / share / default），
        不传时按用户消息自动路由。
        """
        await self._ensure_tools()
        messages: list[dict[str, Any]] = [
            {"role": "system", "content": resolve_system_prompt(prompt_key, user_message)}
        ]
        messages.extend(history or [])
        messages.append({"role": "user", "content": user_message})

        for _ in range(MAX_TOOL_TURNS):
            answer = await self.llm.chat(messages, tools=self._openai_tools)
            tool_calls = self.llm.last_tool_calls
            if not tool_calls:
                return answer or "（模型未返回内容）"

            # 将 assistant 的工具调用意图加入上下文
            messages.append(
                {
                    "role": "assistant",
                    "content": None,
                    "tool_calls": tool_calls,
                }
            )
            for tc in tool_calls:
                fn = tc.get("function", {})
                name = fn.get("name", "")
                try:
                    args = json.loads(fn.get("arguments") or "{}")
                except json.JSONDecodeError:
                    args = {}
                try:
                    result = await self.mcp.call_tool(name, args, self.user_id)
                except MCPClientError as exc:
                    result = f"工具执行失败: {exc}"
                except Exception as exc:  # 工具异常不应中断对话
                    result = f"工具执行异常: {exc}"
                messages.append(
                    {"role": "tool", "tool_call_id": tc.get("id"), "content": result}
                )

        return "（已达最大工具调用轮次，请换一种问法）"

    async def stream_run(
        self,
        user_message: str,
        history: list[dict[str, Any]] | None = None,
        confirm: Callable[[str, dict[str, Any], str], Awaitable[bool]] | None = None,
        prompt_key: str | None = None,
    ) -> AsyncIterator[dict[str, Any]]:
        """流式运行：产出事件 dict，供 SSE 接口消费。

        事件类型：
        - confirmation_required: 敏感操作等待用户批准 {"type","requestId","tool","arguments"}
        - tool_start: 开始调用工具 {"type","tool","arguments"}
        - tool_end:   工具执行完成 {"type","tool","result","error"?,"skipped"?}
        - delta:      LLM 回答内容增量 {"type","content"}
        - done:       回答结束 {"type","content"}
        - error:      异常 {"type","content"}

        confirm 回调签名：confirm(tool_name, arguments, request_id) -> bool。
        返回 True 表示用户批准执行，False 表示拒绝（工具不会执行）。
        prompt_key: 指定提示词场景（search / file_ops / share / default），
        不传时按用户消息自动路由。
        """
        await self._ensure_tools()
        messages: list[dict[str, Any]] = [
            {"role": "system", "content": resolve_system_prompt(prompt_key, user_message)}
        ]
        messages.extend(history or [])
        messages.append({"role": "user", "content": user_message})

        # 模型承诺继续搜索但未发起工具调用的兜底强制次数，避免无限循环
        continue_prompted = 0
        for _ in range(MAX_TOOL_TURNS):
            t0 = time.perf_counter()
            answer = await self.llm.chat(messages, tools=self._openai_tools)
            llm_ms = int((time.perf_counter() - t0) * 1000)
            tool_calls = self.llm.last_tool_calls
            if not tool_calls:
                if (
                    answer
                    and continue_prompted < 2
                    and _looks_like_promised_search(answer)
                ):
                    # 模型说"我再搜一下"却未调用工具：强制它真正发起搜索
                    continue_prompted += 1
                    yield {
                        "type": "llm_decision",
                        "tools": [],
                        "duration_ms": llm_ms,
                        "nudge": True,
                    }
                    messages.append(
                        {
                            "role": "user",
                            "content": "你刚才说还要继续搜索/检查。请直接调用搜索工具完成并如实汇报结果，不要只描述意图而不调用工具。",
                        }
                    )
                    continue
                # 无工具调用：流式输出最终答案增量
                yield {"type": "llm_decision", "tools": [], "duration_ms": llm_ms}
                async for piece in self.llm.stream(messages, tools=self._openai_tools):
                    yield {"type": "delta", "content": piece}
                yield {"type": "done", "content": answer or ""}
                return

            yield {
                "type": "llm_decision",
                "tools": [tc.get("function", {}).get("name", "") for tc in tool_calls],
                "duration_ms": llm_ms,
            }
            messages.append(
                {
                    "role": "assistant",
                    "content": None,
                    "tool_calls": tool_calls,
                }
            )
            for tc in tool_calls:
                fn = tc.get("function", {})
                name = fn.get("name", "")
                try:
                    args = json.loads(fn.get("arguments") or "{}")
                except json.JSONDecodeError:
                    args = {}
                # 敏感操作（移动/删除）必须获得用户批准后才执行
                if confirm is not None and name in SENSITIVE_TOOLS:
                    request_id = uuid.uuid4().hex
                    yield {
                        "type": "confirmation_required",
                        "requestId": request_id,
                        "tool": name,
                        "arguments": args,
                    }
                    approved = await confirm(name, args, request_id)
                    if not approved:
                        result = "用户拒绝了该操作，未执行。"
                        yield {
                            "type": "tool_end",
                            "tool": name,
                            "result": result,
                            "duration_ms": 0,
                            "skipped": True,
                        }
                        messages.append(
                            {
                                "role": "tool",
                                "tool_call_id": tc.get("id"),
                                "content": result,
                            }
                        )
                        continue

                yield {"type": "tool_start", "tool": name, "arguments": args}
                t0 = time.perf_counter()
                try:
                    result = await self.mcp.call_tool(name, args, self.user_id)
                    yield {
                        "type": "tool_end",
                        "tool": name,
                        "result": result,
                        "duration_ms": int((time.perf_counter() - t0) * 1000),
                    }
                except Exception as exc:
                    result = f"工具执行异常: {exc}"
                    yield {
                        "type": "tool_end",
                        "tool": name,
                        "result": result,
                        "duration_ms": int((time.perf_counter() - t0) * 1000),
                        "error": True,
                    }
                messages.append(
                    {"role": "tool", "tool_call_id": tc.get("id"), "content": result}
                )

        yield {"type": "error", "content": "已达最大工具调用轮次"}
