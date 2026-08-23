"""会话持久化：r_pan_agent_session / r_pan_agent_message / r_pan_agent_trace（与云盘同库）。"""
import json
import uuid
from datetime import datetime
from typing import Any

from sqlalchemy import (
    BigInteger,
    Column,
    DateTime,
    Integer,
    MetaData,
    String,
    Table,
    Text,
    delete,
    desc,
    insert,
    inspect,
    select,
    text,
    update,
)
from sqlalchemy.ext.asyncio import create_async_engine

from app.config import settings

metadata = MetaData()

session_table = Table(
    "r_pan_agent_session",
    metadata,
    Column("session_id", String(64), primary_key=True),
    Column("user_id", BigInteger, nullable=False),
    Column("title", String(255), nullable=False, server_default=""),
    Column("created_at", DateTime, nullable=False),
    Column("updated_at", DateTime, nullable=False),
)

message_table = Table(
    "r_pan_agent_message",
    metadata,
    Column("id", Integer, primary_key=True, autoincrement=True),
    Column("session_id", String(64), nullable=False),
    Column("role", String(16), nullable=False),
    Column("content", Text, nullable=False),
    Column("tool_name", String(64), nullable=True),
    Column("trace_id", String(64), nullable=True),
    Column("created_at", DateTime, nullable=False),
)

# 链路追踪：一次对话 = 一个 trace_id，记录 message_in/llm_decision/tool_call/answer/error 各步骤
trace_table = Table(
    "r_pan_agent_trace",
    metadata,
    # 主键：MySQL 用 BIGINT，SQLite 用 INTEGER（BIGINT 在 SQLite 不会自增）
    Column(
        "id",
        BigInteger().with_variant(Integer, "sqlite"),
        primary_key=True,
        autoincrement=True,
    ),
    Column("trace_id", String(64), nullable=False, index=True),
    Column("session_id", String(64), nullable=False),
    Column("user_id", BigInteger, nullable=False),
    Column("step_type", String(32), nullable=False),
    Column("step_name", String(128), nullable=True),
    Column("payload", Text, nullable=True),
    Column("status", String(16), nullable=False, server_default="ok"),
    Column("duration_ms", Integer, nullable=True),
    Column("created_at", DateTime, nullable=False),
)


class SessionStore:
    """Agent 会话存储。生产用 MySQL，测试可注入 SQLite 内存库。"""

    def __init__(self, url: str | None = None) -> None:
        self.url = url or settings.mysql_url
        self._engine = None

    async def init(self) -> None:
        if self._engine is not None:
            return
        self._engine = create_async_engine(self.url)
        async with self._engine.begin() as conn:
            await conn.run_sync(metadata.create_all)
            await conn.run_sync(self._migrate)

    @staticmethod
    def _migrate(conn: Any) -> None:
        """轻量迁移：老库的 r_pan_agent_message 表补充 trace_id 列（幂等）。"""
        if not inspect(conn).has_table("r_pan_agent_message"):
            return
        cols = {c["name"] for c in inspect(conn).get_columns("r_pan_agent_message")}
        if "trace_id" not in cols:
            conn.execute(
                text("ALTER TABLE r_pan_agent_message ADD COLUMN trace_id VARCHAR(64) NULL")
            )

    async def close(self) -> None:
        if self._engine is not None:
            await self._engine.dispose()
            self._engine = None

    async def create_session(self, user_id: int, title: str = "") -> str:
        await self.init()
        session_id = uuid.uuid4().hex
        now = datetime.now()
        async with self._engine.begin() as conn:
            await conn.execute(
                insert(session_table).values(
                    session_id=session_id,
                    user_id=user_id,
                    title=title,
                    created_at=now,
                    updated_at=now,
                )
            )
        return session_id

    async def get_session(self, session_id: str) -> dict[str, Any] | None:
        """按 session_id 查询会话（含 user_id，用于归属校验）。"""
        await self.init()
        async with self._engine.connect() as conn:
            result = await conn.execute(
                select(
                    session_table.c.session_id,
                    session_table.c.user_id,
                    session_table.c.title,
                    session_table.c.created_at,
                    session_table.c.updated_at,
                ).where(session_table.c.session_id == session_id)
            )
            row = result.mappings().first()
        return dict(row) if row else None

    async def list_sessions(self, user_id: int, limit: int = 20) -> list[dict[str, Any]]:
        await self.init()
        async with self._engine.connect() as conn:
            result = await conn.execute(
                select(
                    session_table.c.session_id,
                    session_table.c.title,
                    session_table.c.updated_at,
                )
                .where(session_table.c.user_id == user_id)
                .order_by(desc(session_table.c.updated_at))
                .limit(limit)
            )
            rows = result.mappings().all()
        return [dict(r) for r in rows]

    async def save_message(
        self,
        session_id: str,
        role: str,
        content: str,
        tool_name: str | None = None,
        trace_id: str | None = None,
    ) -> None:
        await self.init()
        now = datetime.now()
        async with self._engine.begin() as conn:
            await conn.execute(
                insert(message_table).values(
                    session_id=session_id,
                    role=role,
                    content=content,
                    tool_name=tool_name,
                    trace_id=trace_id,
                    created_at=now,
                )
            )
            await conn.execute(
                update(session_table)
                .where(session_table.c.session_id == session_id)
                .values(updated_at=now)
            )

    async def load_history(
        self, session_id: str, limit: int = 20
    ) -> list[dict[str, Any]]:
        await self.init()
        async with self._engine.connect() as conn:
            result = await conn.execute(
                select(
                    message_table.c.role,
                    message_table.c.content,
                    message_table.c.tool_name,
                    message_table.c.trace_id,
                )
                .where(message_table.c.session_id == session_id)
                .order_by(desc(message_table.c.id))
                .limit(limit)
            )
            rows = list(reversed(result.mappings().all()))
        return [
            {
                "role": r["role"],
                "content": r["content"],
                "tool_name": r["tool_name"],
                "trace_id": r["trace_id"],
            }
            for r in rows
        ]

    # ---------- 链路追踪 ----------

    async def save_trace(
        self,
        trace_id: str,
        session_id: str,
        user_id: int,
        step_type: str,
        payload: dict[str, Any] | None = None,
        step_name: str | None = None,
        status: str = "ok",
        duration_ms: int | None = None,
    ) -> None:
        """写入一个链路步骤。payload 序列化为 JSON 存储。"""
        await self.init()
        async with self._engine.begin() as conn:
            await conn.execute(
                insert(trace_table).values(
                    trace_id=trace_id,
                    session_id=session_id,
                    user_id=user_id,
                    step_type=step_type,
                    step_name=step_name,
                    payload=(
                        json.dumps(payload, ensure_ascii=False)
                        if payload is not None
                        else None
                    ),
                    status=status,
                    duration_ms=duration_ms,
                    created_at=datetime.now(),
                )
            )

    async def trace_steps(self, trace_id: str) -> list[dict[str, Any]]:
        """按 trace_id 读取完整链路步骤（按写入顺序）。"""
        await self.init()
        async with self._engine.connect() as conn:
            result = await conn.execute(
                select(
                    trace_table.c.user_id,
                    trace_table.c.step_type,
                    trace_table.c.step_name,
                    trace_table.c.payload,
                    trace_table.c.status,
                    trace_table.c.duration_ms,
                    trace_table.c.created_at,
                )
                .where(trace_table.c.trace_id == trace_id)
                .order_by(trace_table.c.id)
            )
            rows = result.mappings().all()
        return [
            {
                "userId": str(r["user_id"]),
                "stepType": r["step_type"],
                "stepName": r["step_name"],
                "payload": json.loads(r["payload"]) if r["payload"] else None,
                "status": r["status"],
                "durationMs": r["duration_ms"],
                "createdAt": (
                    r["created_at"].isoformat() if r["created_at"] else None
                ),
            }
            for r in rows
        ]

    async def trace_ids_by_session(
        self, session_id: str, user_id: int, limit: int = 50
    ) -> list[dict[str, Any]]:
        """列出会话内的链路概要：trace_id、首末时间、步骤数、总耗时。"""
        await self.init()
        async with self._engine.connect() as conn:
            result = await conn.execute(
                select(
                    trace_table.c.trace_id,
                    trace_table.c.created_at,
                    trace_table.c.duration_ms,
                )
                .where(
                    (trace_table.c.session_id == session_id)
                    & (trace_table.c.user_id == user_id)
                )
                .order_by(trace_table.c.id)
            )
            rows = result.mappings().all()
        grouped: dict[str, dict[str, Any]] = {}
        for r in rows:
            g = grouped.setdefault(
                r["trace_id"],
                {"traceId": r["trace_id"], "stepCount": 0, "totalMs": 0, "createdAt": None},
            )
            g["stepCount"] += 1
            if r["duration_ms"]:
                g["totalMs"] += r["duration_ms"]
            if g["createdAt"] is None:
                g["createdAt"] = r["created_at"].isoformat()
        return list(grouped.values())[-limit:]

    async def delete_session(self, session_id: str) -> None:
        await self.init()
        async with self._engine.begin() as conn:
            await conn.execute(
                delete(message_table).where(message_table.c.session_id == session_id)
            )
            await conn.execute(
                delete(session_table).where(session_table.c.session_id == session_id)
            )
