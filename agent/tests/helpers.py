"""LangGraph 节点/图测试替身。

ScriptedModel：按脚本顺序返回 AIMessage（或由 callable 按需生成），
只实现节点/图依赖的 ainvoke / astream 接口。
"""
from typing import Any, Awaitable, Callable


class FakeWriter:
    """捕获节点经 get_stream_writer() 发出的事件（LangGraph 1.x 为可调用 writer）。"""

    def __init__(self) -> None:
        self.events: list[dict[str, Any]] = []

    def __call__(self, data: dict[str, Any]) -> None:
        self.events.append(data)


class ScriptedModel:
    """脚本驱动模型：每次 ainvoke 弹出脚本下一项并记录调用。"""

    def __init__(self, script: list[Any]) -> None:
        # 元素可为 AIMessage / str / callable(messages)->AIMessage
        self._script = list(script)
        self.calls: list[list[Any]] = []

    async def ainvoke(self, messages: list[Any]) -> Any:
        self.calls.append(messages)
        item = self._script.pop(0)
        if isinstance(item, str):
            from langchain_core.messages import AIMessage

            return AIMessage(content=item)
        if callable(item):
            return item(messages)
        return item

    async def astream(self, messages: list[Any]):
        """逐字增量产出（供 summarizer/最终回答流式测试）。"""
        self.calls.append(messages)
        item = self._script.pop(0)
        text = item.content if isinstance(item, str) else str(getattr(item, "content", item))
        from langchain_core.messages import AIMessage

        # 不直接在这里构造 chunk，交由具体测试覆盖；默认整段一次性产出
        yield AIMessage(content=text)


class StubStructured:
    """绕过 structured output 解析：按脚本顺序返回预设的 pydantic 实例。"""

    def __init__(self, script: list[Any]) -> None:
        self._script = list(script)
        self.calls: list[list[Any]] = []

    async def ainvoke(self, messages: list[Any]) -> Any:
        self.calls.append(messages)
        return self._script.pop(0)
