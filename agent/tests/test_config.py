from app.config import Settings


def test_settings_defaults():
    s = Settings()
    assert s.pan_base_url == "http://127.0.0.1:10000"
    assert s.mcp_server_url == "http://127.0.0.1:10001/mcp"
    assert s.agent_port == 8000


def test_checkpoint_and_llm_config_defaults():
    s = Settings()
    assert s.mysql_checkpoint_url == "mysql://root:123456@127.0.0.1:3306/cloud_drive_pan?charset=utf8mb4"
    assert s.llm_temperature == 0.3
    assert s.graph_recursion_limit == 50
    assert s.worker_max_iterations == 3
