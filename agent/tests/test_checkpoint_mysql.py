"""MySQL Checkpointer 跨重启断点（集成测试）。

默认跳过：本地无 MySQL / 未显式配置 AGENT_MYSQL_CHECKPOINT_URL 时不运行。
验证点：同一 thread 被 interrupt 挂起后，用"新连接实例"恢复可继续执行
（等价跨进程重启 resume），且 setup() 幂等可重复调用。
"""
import os
from typing import TypedDict

import pytest
from langgraph.graph import START, StateGraph
from langgraph.types import Command, interrupt

from app.core.checkpoint import open_mysql_checkpointer

pytestmark = [
    pytest.mark.integration,
    pytest.mark.skipif(
        not os.getenv("AGENT_MYSQL_CHECKPOINT_URL"),
        reason="需显式配置 AGENT_MYSQL_CHECKPOINT_URL 且本地 MySQL 可用",
    ),
]


class _S(TypedDict):
    x: int


def _graph():
    def node_a(state: _S):
        return {"x": state.get("x", 0) + 1}

    def node_b(state: _S):
        val = interrupt({"ask": "continue?"})
        return {"x": state["x"] + (10 if val else 0)}

    g = StateGraph(_S)
    g.add_node("a", node_a)
    g.add_node("b", node_b)
    g.add_edge(START, "a")
    g.add_edge("a", "b")
    return g


async def _collect_values(compiled, payload, cfg):
    last = None
    async for chunk in compiled.astream(payload, config=cfg, stream_mode="values"):
        last = chunk
    return last


@pytest.mark.asyncio
async def test_mysql_checkpointer_cross_restart_resume():
    url = os.environ["AGENT_MYSQL_CHECKPOINT_URL"]
    cfg = {"configurable": {"thread_id": "it-cross-restart-1"}}

    # 第一次运行：node_a 执行后被 node_b 的 interrupt 挂起（写盘到 MySQL）
    cp1 = await open_mysql_checkpointer(url)
    compiled1 = _graph().compile(checkpointer=cp1)
    last = await _collect_values(
        compiled1, {"x": 1}, cfg
    )
    # interrupt 前 node_a 已把 x 更新为 2
    assert last["x"] == 2

    # 模拟进程重启：新连接实例 + setup() 幂等，读取同一 thread 并 resume
    cp2 = await open_mysql_checkpointer(url)
    await cp2.setup()  # 重复 setup 应安全
    compiled2 = _graph().compile(checkpointer=cp2)
    resumed = await _collect_values(
        compiled2, Command(resume=True), cfg
    )
    assert resumed["x"] == 12  # 2 + 10

    # 顺带验证 aget 能读到已持久化的 checkpoint（跨实例读）
    tup = await cp2.aget_tuple(cfg)
    assert tup is not None
