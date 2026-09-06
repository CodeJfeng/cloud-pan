from unittest.mock import patch

import pytest

from app.config import Settings
from app.core.model import LLMError, build_chat_model


def test_build_chat_model_uses_openai_compatible_settings():
    # 显式传值避免被环境变量（AGENT_LLM_*）覆盖导致断言不稳定
    s = Settings(
        llm_api_key="test-key",
        llm_base_url="https://api.deepseek.com/v1",
        llm_model="deepseek-chat",
        llm_temperature=0.3,
    )
    with patch("app.core.model.ChatOpenAI") as MockChat:
        build_chat_model(s)
        kwargs = MockChat.call_args.kwargs
        assert kwargs["base_url"].rstrip("/") == "https://api.deepseek.com/v1"
        assert kwargs["api_key"] == "test-key"
        assert kwargs["model"] == "deepseek-chat"
        assert kwargs["temperature"] == 0.3
        assert kwargs["streaming"] is True


def test_build_chat_model_requires_api_key():
    """与旧 LLMClient 行为一致：空 api_key 在构建时抛 LLMError。"""
    with pytest.raises(LLMError):
        build_chat_model(Settings(llm_api_key=""))
