"""把 MCP 工具转成 LangGraph 可绑定的 LangChain BaseTool。

MCP Server 的 schema 含内部参数 userId；对 LLM 暴露前剔除，
执行时由工具闭包注入构造时绑定的 userId。
"""
from typing import Any, Optional, Iterable

from langchain_core.tools import StructuredTool
from pydantic import BaseModel, Field, create_model

from app.mcp.client import McpToolInfo

# 敏感工具：执行前必须获得用户批准（移动/删除类操作，不可撤销或影响面大）
SENSITIVE_TOOLS = frozenset({"transfer_files", "delete_file"})

# Worker 垂直分工：子任务 worker_group -> 允许的工具名集合（均含 list_files 作为定位辅助）
TOOL_GROUP: dict[str, frozenset[str]] = {
    "search": frozenset({"search_files", "list_files"}),
    "file_ops": frozenset(
        {
            "list_files",
            "create_folder",
            "rename_file",
            "delete_file",
            "transfer_files",
            "copy_files",
            "list_recycles",
            "restore_recycle",
        }
    ),
    "share": frozenset({"list_files", "create_share"}),
    "recycle": frozenset({"list_recycles", "restore_recycle", "list_files"}),
}
ALL_TOOLS = frozenset().union(*TOOL_GROUP.values())


def tool_group(tool_name: str) -> str:
    """返回工具所属的 Worker 域（search / file_ops / share / recycle）。"""
    for group, names in TOOL_GROUP.items():
        if tool_name in names:
            return group
    return "search"


def _py_type(prop: dict[str, Any]) -> Any:
    """把 MCP JSON Schema 类型映射到 Python 类型（pydantic 字段用）。"""
    t = prop.get("type")
    if t == "integer":
        return int
    if t == "number":
        return float
    if t == "boolean":
        return bool
    if t == "array":
        items = prop.get("items", {})
        item_type = items.get("type") if isinstance(items, dict) else None
        if item_type == "string":
            return list[str]
        if item_type == "integer":
            return list[int]
        return list
    return str


def _args_model(name: str, input_schema: dict[str, Any]) -> type[BaseModel]:
    """由 MCP schema 生成 pydantic 参数模型；剔除内部注入参数 userId。"""
    props = input_schema.get("properties", {})
    required = set(input_schema.get("required", []) or [])
    fields: dict[str, Any] = {}
    for field, prop in props.items():
        if field == "userId":
            continue  # 内部注入参数，不出现在 LLM 可见 schema
        typ = _py_type(prop)
        desc = prop.get("description", "")
        if field in required:
            fields[field] = (typ, Field(description=desc))
        else:
            fields[field] = (Optional[typ], Field(default=None, description=desc))
    return create_model(f"{name}Args", **fields)


class McpToolFactory:
    """把一次 MCP 会话中的工具转换为 LangChain StructuredTool。

    user_id 在构造时绑定，工具执行时自动注入（不出现在 LLM 可见 schema）。
    """

    def __init__(self, mcp, user_id: str):
        self._mcp = mcp
        self._user_id = user_id

    def _to_tool(self, info: McpToolInfo) -> StructuredTool:
        async def invoke(**kwargs: Any) -> str:
            return await self._mcp.call_tool(info.name, kwargs, self._user_id)

        return StructuredTool.from_function(
            coroutine=invoke,
            name=info.name,
            description=info.description or "",
            args_schema=_args_model(info.name, info.input_schema),
        )

    async def build_all(self, names: Iterable[str] | None = None) -> list[StructuredTool]:
        """拉取 MCP 工具清单并转换为 LangChain 工具。

        names 为 None 时返回全部已映射工具；否则仅返回给定名字集合内的工具。
        """
        infos = await self._mcp.list_tools()
        allow = set(names) if names is not None else set(ALL_TOOLS)
        return [self._to_tool(t) for t in infos if t.name in allow]
