"""Checkpointer 工厂：测试/无库环境用 MemorySaver，生产用 MySQL AIOMySQLSaver。

MySQL 路径说明：社区库 langgraph-checkpoint-mysql v3 的
``AIOMySQLSaver.from_conn_string(url)`` 返回 AsyncIterator（与官方
PostgresSaver 同款生命周期），需在 async 环境取实例并 ``await setup()``
建表；任务 11 统一接线（应用级单例，跨重启断点）。
"""
from typing import Any

from langgraph.checkpoint.memory import MemorySaver


def build_checkpointer(*, force_memory: bool = True) -> Any:
    """返回可编译进图的 checkpointer。

    任务 11 前生产 MySQL 路径未接线：调用方显式 force_memory=True
    用内存版（测试/单进程），避免误连库。
    """
    if force_memory:
        return MemorySaver()
    raise RuntimeError(
        "MySQL checkpointer 需在 async 环境通过 open_mysql_checkpointer() 打开"
        "（AIOMySQLSaver.from_conn_string 返回 AsyncIterator），任务 11 接线后可用。"
    )
