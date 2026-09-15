"""日志基础设施：统一格式（含 trace_id）、控制台 + 轮转文件输出。

trace_id 由请求入口（app/api/chat.py）写入 ContextVar，LangGraph 节点与它派生
的异步任务（Send 扇出的 worker）会自动继承，无需逐层传参。因此同一请求的所有
日志——包括 LLM 错误回调、工具异常、接口异常——都能用 trace_id 串成一条，
并与链路表（agent_trace）里的 traceId 对齐，便于线上排查。
"""
import logging
import logging.handlers
import sys
from contextvars import ContextVar, Token
from pathlib import Path

from app.config import settings

# 请求级追踪 ID；非请求上下文（启动、定时任务）为 "-"
_trace_id: ContextVar[str] = ContextVar("trace_id", default="-")

LOG_FORMAT = "%(asctime)s | %(levelname)-7s | %(name)s | trace=%(trace_id)s | %(message)s"
DATE_FORMAT = "%Y-%m-%d %H:%M:%S"
# 单文件 5MB × 3 个备份
MAX_BYTES = 5 * 1024 * 1024
BACKUP_COUNT = 3
# 第三方库降噪：这些库在 INFO 级别会刷屏（每次 HTTP 调用一行）
NOISY_LOGGERS = ("httpx", "httpcore", "openai", "urllib3", "aiomysql")


def set_trace_id(trace_id: str) -> Token:
    """在请求入口设置当前 trace_id（返回 token，便于需要时 reset）。"""
    return _trace_id.set(trace_id or "-")


def get_trace_id() -> str:
    return _trace_id.get()


def reset_trace_id(token: Token) -> None:
    _trace_id.reset(token)


class TraceIdFilter(logging.Filter):
    """把 ContextVar 中的 trace_id 注入每条日志记录，供 formatter 使用。"""

    def filter(self, record: logging.LogRecord) -> bool:
        record.trace_id = get_trace_id()
        return True


def _force_utf8(stream: object) -> None:
    """把真实标准流切到 UTF-8：Windows 默认 GBK，重定向/管道下中文日志会乱码。

    只处理 sys.__stdout__ / sys.__stderr__（原始流），不碰被测试框架替换过的
    sys.stdout/sys.stderr，避免干扰捕获。
    """
    if stream is None or not hasattr(stream, "reconfigure"):
        return
    try:
        stream.reconfigure(encoding="utf-8", errors="replace")  # type: ignore[attr-defined]
    except (OSError, ValueError):
        pass


def setup_logging(level: str | None = None, log_file: str | None = None) -> None:
    """配置根 logger，幂等（重复调用不会重复挂 handler，uvicorn reload 安全）。

    日志目录不可写时降级为仅控制台输出，不让日志问题阻断服务启动。
    """
    root = logging.getLogger()
    if getattr(root, "_agent_logging_configured", False):
        return

    root.setLevel((level or settings.log_level or "INFO").upper())

    _force_utf8(sys.__stdout__)
    _force_utf8(sys.__stderr__)

    formatter = logging.Formatter(LOG_FORMAT, datefmt=DATE_FORMAT)
    trace_filter = TraceIdFilter()

    console = logging.StreamHandler()
    console.setFormatter(formatter)
    console.addFilter(trace_filter)
    root.addHandler(console)

    path = Path(log_file or settings.log_file or "logs/agent.log")
    try:
        path.parent.mkdir(parents=True, exist_ok=True)
        file_handler = logging.handlers.RotatingFileHandler(
            path,
            maxBytes=MAX_BYTES,
            backupCount=BACKUP_COUNT,
            encoding="utf-8",
        )
        file_handler.setFormatter(formatter)
        file_handler.addFilter(trace_filter)
        root.addHandler(file_handler)
    except OSError as exc:
        root.warning("日志文件不可写，仅输出到控制台：%s", exc)

    for name in NOISY_LOGGERS:
        logging.getLogger(name).setLevel(logging.WARNING)

    root._agent_logging_configured = True  # type: ignore[attr-defined]
