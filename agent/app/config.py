"""云盘 Agent 服务配置。"""
from pydantic_settings import BaseSettings


class Settings(BaseSettings):
    """服务配置，支持环境变量覆盖（前缀 AGENT_）。"""

    # 云盘 Java 后端地址
    pan_base_url: str = "http://127.0.0.1:10000"
    # MCP Server 地址
    mcp_server_url: str = "http://127.0.0.1:10001/mcp"
    # Agent 服务监听端口
    agent_port: int = 8000
    # 会话存储（MySQL，与云盘同库）
    mysql_url: str = "mysql+aiomysql://root:123456@127.0.0.1:3306/cloud_drive_pan?charset=utf8mb4"
    # LLM 配置（OpenAI 兼容协议）
    llm_base_url: str = "https://api.deepseek.com/v1"
    llm_api_key: str = ""
    llm_model: str = "deepseek-chat"
    llm_temperature: float = 0.3

    # LangGraph 执行状态 Checkpointer（MySQL，与业务库同实例；值不含 aiomysql 驱动前缀）
    mysql_checkpoint_url: str = "mysql://root:123456@127.0.0.1:3306/cloud_drive_pan?charset=utf8mb4"

    # LangGraph 图执行限制
    graph_recursion_limit: int = 50
    worker_max_iterations: int = 3
    react_max_iterations: int = 8

    model_config = {"env_prefix": "AGENT_"}


settings = Settings()
