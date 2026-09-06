"""spike：验证 langgraph 1.2.11 interrupt + stream_mode 形态（临时文件，验证后删除）。"""
import asyncio
from typing import TypedDict

from langgraph.checkpoint.memory import MemorySaver
from langgraph.config import get_stream_writer
from langgraph.graph import START, StateGraph
from langgraph.types import Command, interrupt


class S(TypedDict):
    x: str


def node(s: S):
    get_stream_writer()({"type": "custom_hi"})
    val = interrupt({"tool": "delete_file", "requestId": "rid-1"})
    return {"x": f"resumed:{val}"}


async def main():
    g = StateGraph(S).add_node("n", node)
    g.add_edge(START, "n")
    compiled = g.compile(checkpointer=MemorySaver())
    cfg = {"configurable": {"thread_id": "spike1"}}
    async for item in compiled.astream(
        {"x": "start"}, stream_mode=["custom", "updates"], config=cfg
    ):
        if isinstance(item, tuple):
            mode, chunk = item
            print("TUPLE", mode, type(chunk).__name__, repr(chunk)[:250])
        else:
            print("ITEM", type(item).__name__, repr(item)[:250])
    print("--- resume ---")
    async for item in compiled.astream(
        Command(resume=True), stream_mode=["custom", "updates"], config=cfg
    ):
        if isinstance(item, tuple):
            mode, chunk = item
            print("TUPLE", mode, type(chunk).__name__, repr(chunk)[:250])
        else:
            print("ITEM", type(item).__name__, repr(item)[:250])


asyncio.run(main())
