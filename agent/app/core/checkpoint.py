"""Checkpointer 工厂与进程级 MySQL 打开器。

- build_checkpointer(force_memory=True)：内存版（单测/单进程临时运行）
- open_mysql_checkpointer()：进程级长连接版 MySQL saver（生产，任务 11 起启用）

MySQL 连接说明：langgraph-checkpoint-mysql 的 ``from_conn_string`` 是
asynccontextmanager——退出即关闭 aiomysql 连接，不适合作为应用级单例；
因此这里自建并持有 aiomysql 长连接（autocommit=True），返回的 saver 与
进程同生命周期。``setup()`` 幂等建表（checkpoints / checkpoint_blobs /
checkpoint_writes），重复调用安全。
"""
from typing import Any

from langgraph.checkpoint.memory import MemorySaver


def build_checkpointer(*, force_memory: bool = True) -> Any:
    """返回可编译进图的 checkpointer。

    force_memory=True 返回 MemorySaver（测试/无库环境）；
    False 需异步打开 MySQL（见 open_mysql_checkpointer），故在此拒绝同步创建。
    """
    if force_memory:
        return MemorySaver()
    raise RuntimeError(
        "MySQL checkpointer 需异步打开（aiomysql 长连接 + await setup()），"
        "请改用 app.core.checkpoint.open_mysql_checkpointer()。"
    )


async def open_mysql_checkpointer(url: str | None = None) -> Any:
    """打开进程级 MySQL checkpointer（含幂等 setup），失败时抛出连接异常。

    url 默认取 settings.mysql_checkpoint_url（同库不同表，业务表不受影响）。
    """
    from langgraph.checkpoint.mysql.aio import AIOMySQLSaver

    import aiomysql

    from app.config import settings

    url = url or settings.mysql_checkpoint_url
    if not url:
        raise RuntimeError("未配置 mysql_checkpoint_url，无法打开 MySQL checkpointer")
    conn = await aiomysql.connect(
        **AIOMySQLSaver.parse_conn_string(url), autocommit=True
    )
    cp = AIOMySQLSaver(conn=conn)
    await cp.setup()
    return cp
