from app.config import Settings


def test_settings_defaults():
    s = Settings()
    assert s.pan_base_url == "http://127.0.0.1:10000"
    assert s.mcp_server_url == "http://127.0.0.1:10001/mcp"
    assert s.agent_port == 8000
