import pytest

from app.core.pan_tools import (
    ALL_TOOLS,
    SENSITIVE_TOOLS,
    McpToolFactory,
    tool_group,
)
from app.mcp.client import McpToolInfo

MCP_TOOLS = [
    McpToolInfo(
        name="search_files",
        description="搜索文件",
        input_schema={
            "type": "object",
            "properties": {
                "userId": {"type": "string"},
                "keyword": {"type": "string"},
            },
            "required": ["userId", "keyword"],
        },
    ),
    McpToolInfo(
        name="delete_file",
        description="删除（敏感）",
        input_schema={
            "type": "object",
            "properties": {
                "fileIds": {"type": "array", "items": {"type": "string"}}
            },
            "required": ["fileIds"],
        },
    ),
]


class _FakeMcp:
    """与 MCPClient 相同的最小接口：list_tools / call_tool。"""

    def __init__(self, tools):
        self._tools = tools
        self.calls = []

    async def list_tools(self):
        return self._tools

    async def call_tool(self, name, arguments, user_id):
        self.calls.append((name, arguments, user_id))
        return "ok"


@pytest.mark.asyncio
async def test_tool_schema_drops_user_id_and_injects_on_call():
    fake = _FakeMcp(MCP_TOOLS)
    factory = McpToolFactory(fake, "u_1")
    tools = await factory.build_all()

    assert len(tools) == 2
    # schema 不含 userId
    schema = tools[0].args_schema.model_json_schema()
    assert "userId" not in schema["properties"]
    assert "userId" not in schema.get("required", [])
    # 调用时注入构造时绑定的 user_id
    result = await tools[0].ainvoke({"keyword": "报告"})
    assert fake.calls == [("search_files", {"keyword": "报告"}, "u_1")]
    assert result == "ok"


def test_sensitive_flag_and_grouping():
    assert SENSITIVE_TOOLS == frozenset({"transfer_files", "delete_file"})
    assert tool_group("delete_file") == "file_ops"
    assert tool_group("create_share") == "share"
    assert "list_files" in ALL_TOOLS
    assert "restore_recycle" in ALL_TOOLS
