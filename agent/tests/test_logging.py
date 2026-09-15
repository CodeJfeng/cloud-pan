"""日志基础设施测试：trace_id 注入、文件输出、LLM 错误回调、失败降级日志。"""
import logging

import pytest
from langchain_core.messages import HumanMessage

from app.core.llm_logging import LLMErrorLoggingHandler
from app.core.logging_config import (
    TraceIdFilter,
    get_trace_id,
    reset_trace_id,
    set_trace_id,
    setup_logging,
)
from app.core.nodes.intent import IntentDeps, intent


@pytest.fixture
def clean_root_logging(monkeypatch):
    """隔离 setup_logging 的全局副作用：记录既有 handler，用完还原。"""
    root = logging.getLogger()
    before = list(root.handlers)
    monkeypatch.delattr(root, "_agent_logging_configured", raising=False)
    yield root
    for handler in list(root.handlers):
        if handler not in before:
            root.removeHandler(handler)
            handler.close()
    root.setLevel(logging.WARNING)
    monkeypatch.delattr(root, "_agent_logging_configured", raising=False)


def test_setup_logging_is_idempotent(clean_root_logging, tmp_path):
    """重复调用（uvicorn reload）不会重复挂 handler。"""
    root = clean_root_logging
    setup_logging(log_file=str(tmp_path / "a.log"))
    count = len(root.handlers)
    assert count >= 2  # 控制台 + 文件
    setup_logging(log_file=str(tmp_path / "a.log"))
    assert len(root.handlers) == count


def test_logs_written_to_file_with_trace_field(clean_root_logging, tmp_path):
    """日志同时落文件，且格式里带 trace 字段。"""
    path = tmp_path / "logs" / "agent.log"
    setup_logging(log_file=str(path))
    logging.getLogger("app.test").error("写入文件测试")
    for handler in clean_root_logging.handlers:
        handler.flush()

    content = path.read_text(encoding="utf-8")
    assert "写入文件测试" in content
    assert "trace=" in content


def test_setup_logging_survives_unwritable_path(clean_root_logging, tmp_path):
    """日志路径不可写时降级为仅控制台，不抛异常（父路径是文件的场景）。"""
    blocker = tmp_path / "blocker"
    blocker.write_text("x", encoding="utf-8")
    setup_logging(log_file=str(blocker / "agent.log"))
    assert len(clean_root_logging.handlers) >= 1


def test_trace_id_filter_injects_context_value():
    record = logging.LogRecord("x", logging.INFO, "f", 1, "msg", None, None)
    token = set_trace_id("abc123")
    try:
        assert TraceIdFilter().filter(record) is True
        assert record.trace_id == "abc123"
        assert get_trace_id() == "abc123"
    finally:
        reset_trace_id(token)
    assert get_trace_id() == "-"


def test_llm_error_handler_logs_model_and_message(caplog):
    handler = LLMErrorLoggingHandler(model_name="deepseek-v4-flash")
    with caplog.at_level(logging.ERROR, logger="app.core.llm_logging"):
        handler.on_llm_error(
            ValueError("Error code: 400 - prompt must contain json"),
            run_id="run-1",
        )
    messages = [r.message for r in caplog.records]
    assert any("LLM 调用失败" in m for m in messages)
    assert any("deepseek-v4-flash" in m for m in messages)
    assert any("Error code: 400" in m for m in messages)


@pytest.mark.asyncio
async def test_intent_gateway_failure_is_logged(caplog):
    """意图识别调用失败：日志可见，且降级 react。"""

    class Boom:
        async def ainvoke(self, messages):
            raise RuntimeError("gateway down")

    with caplog.at_level(logging.WARNING, logger="app.core.nodes.intent"):
        out = await intent(
            {"messages": [HumanMessage(content="你好")]}, IntentDeps(model=Boom())
        )
    assert out["mode"] == "react"
    assert any("降级为 react" in r.message for r in caplog.records)
    assert any("gateway down" in r.message for r in caplog.records)


@pytest.mark.asyncio
async def test_unparsable_output_is_logged(caplog):
    """模型输出不是 JSON：日志留痕，便于定位提示词/模型问题。"""
    from langchain_core.messages import AIMessage

    from tests.helpers import ScriptedModel

    model = ScriptedModel([AIMessage(content="我猜你想找文件")])
    with caplog.at_level(logging.WARNING, logger="app.core.structured"):
        out = await intent(
            {"messages": [HumanMessage(content="弄一下那个")]}, IntentDeps(model=model)
        )
    assert out["mode"] == "clarify"
    assert any("未包含 JSON 对象" in r.message for r in caplog.records)
