"""把 MCP 工具转成 LangGraph 可绑定的 LangChain BaseTool。

MCP Server 的 schema 含内部参数 userId；对 LLM 暴露前剔除，
执行时由工具闭包注入构造时绑定的 userId。

两层参数治理（都是为了让 LLM 少踩后端的坑，而不是靠提示词祈祷）：

1. 根目录 id 归一化（A2）
   `list_files` 的 parentId 支持 "-1" 魔法值（后端 FileController 有特判），
   但 transfer/copy/create_folder 的目录参数直接走 `IdUtil.decrypt()`，传 "-1"
   会抛 `AES128Util.aesDecode fail`。模型很容易从 list_files 类比出"根目录就是 -1"，
   于是第一次调用必然失败。这里在 Agent 侧把魔法值解析成真实的根目录加密 id，
   从根上消除该类失败（顺带省掉一次敏感操作的无谓审批）。

2. 写操作回读校验（C）
   移动/复制/新建成功后自动 list 一次目标目录，把真实内容附加到工具返回值。
   模型因此能基于事实作答，而不是拿 `{"code":0}` 就宣称"已经移过去了"。
   校验失败不影响主结果（静默跳过），只作为额外证据。
"""
import logging
from typing import Any, Iterable, Optional

from langchain_core.tools import StructuredTool
from pydantic import BaseModel, Field, create_model

from app.core.context import parse_file_list
from app.mcp.client import McpToolInfo

logger = logging.getLogger(__name__)

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

# ---------------------------------------------------------------------------
# 根目录 id 归一化
# ---------------------------------------------------------------------------

# 会被当成"根目录"传入的写法（后端只认 list_files 的 -1，其余工具一律解密失败）
ROOT_MAGIC_VALUES = frozenset({"", "-1", "all", "root"})

# 工具名 -> 需要归一化的目录参数（这些参数后端不接受魔法值）
ROOT_ID_PARAMS: dict[str, tuple[str, ...]] = {
    "transfer_files": ("targetParentId",),
    "copy_files": ("targetParentId",),
    "create_folder": ("parentId",),
}

# ---------------------------------------------------------------------------
# 写操作回读校验
# ---------------------------------------------------------------------------

# 工具名 -> 校验时要列出的目录参数（只有位置类写操作才校验"文件现在在哪"）
VERIFY_PARAMS: dict[str, str] = {
    "transfer_files": "targetParentId",
    "copy_files": "targetParentId",
    "create_folder": "parentId",
}


class ToolInputError(RuntimeError):
    """工具入参不合法且无法在 Agent 侧修正（如解析不到根目录 id）。

    抛给调用方（worker/react）后会以 ToolMessage 形式回到模型，模型据此
    改用真实 fileId 重试，而不是继续拿魔法值撞后端的解密异常。
    """


# ---------------------------------------------------------------------------
# 审批可读化：把加密 fileId 翻译成用户看得懂的名称
# ---------------------------------------------------------------------------

# 工具名 -> 中文动作（审批摘要用）
TOOL_ACTIONS: dict[str, str] = {
    "transfer_files": "移动",
    "copy_files": "复制",
    "delete_file": "删除",
    "create_share": "分享",
}

# 索引来源：只有"列目录/搜索"的返回里才带 fileId → 名称的映射
_INDEX_SOURCE_TOOLS = frozenset({"list_files", "search_files"})
# 后端字段不统一：list_files 用 filename，search_files 用 fileName
_NAME_KEYS = ("filename", "fileName", "name")
_ID_KEYS = ("fileId", "file_id")
_PARENT_KEYS = ("parentId", "parent_id")


def _first_value(item: dict[str, Any], keys: tuple[str, ...]) -> Any:
    for key in keys:
        val = item.get(key)
        if val:
            return val
    return None


def tool_group(tool_name: str) -> str:
    """返回工具所属的 Worker 域（search / file_ops / share / recycle）。"""
    for group, names in TOOL_GROUP.items():
        if tool_name in names:
            return group
    return "search"


def pick_root_id(items: list[dict[str, Any]]) -> str | None:
    """从 list_files(parentId=-1) 的结果里挑出用户根文件夹的 fileId。

    判据：顶层文件夹的 parentId 不在这批结果的 fileId 集合里（它的父不在此列表中）。
    退化为列表中第一个文件夹；再不行返回 None（调用方转为明确报错）。
    """
    ids = {it.get("fileId") for it in items if it.get("fileId")}
    folders = [it for it in items if it.get("folderFlag") == 1]
    for it in folders:
        if it.get("parentId") not in ids:
            return str(it.get("fileId"))
    if folders:
        return str(folders[0].get("fileId"))
    return None


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

    def __init__(self, mcp, user_id: str, *, verify_writes: bool = True):
        self._mcp = mcp
        self._user_id = user_id
        self._verify_writes = verify_writes
        self._root_id: str | None = None
        # fileId -> {name, folder, parent}：从列目录/搜索结果积累，供审批展示翻译
        self._file_index: dict[str, dict[str, Any]] = {}

    # ------------------------------------------------------------------ 根目录

    async def resolve_root_id(self) -> str | None:
        """解析用户根文件夹的加密 id（结果缓存，失败返回 None 允许后续重试）。"""
        if self._root_id:
            return self._root_id
        try:
            raw = await self._mcp.call_tool("list_files", {"parentId": "-1"}, self._user_id)
        except Exception as exc:  # 拿不到就不硬撑，交由调用方换成明确报错
            logger.warning("解析根目录 ID 失败：%s", exc)
            return None
        items = parse_file_list(str(raw)) or []
        root = pick_root_id(items)
        if root:
            self._root_id = root
        return root

    async def _normalize_args(self, name: str, kwargs: dict[str, Any]) -> dict[str, Any]:
        """把目录参数里的魔法值换成真实根目录 id。"""
        fields = ROOT_ID_PARAMS.get(name)
        if not fields:
            return kwargs
        patched = dict(kwargs)
        for field in fields:
            val = patched.get(field)
            if not isinstance(val, str) or val.strip().lower() not in ROOT_MAGIC_VALUES:
                continue
            root = await self.resolve_root_id()
            if not root:
                raise ToolInputError(
                    f"{name} 的 {field} 不能是 {val or '空值'}，且当前无法解析根目录 ID。"
                    '请先调用 list_files(parentId="-1") 取得目标文件夹的 fileId 后重试。'
                )
            patched[field] = root
        return patched

    # ------------------------------------------------------------------ 审批可读化

    def _index_result(self, name: str, raw: Any) -> None:
        """从列目录/搜索结果里积累 fileId → 名称索引。"""
        if name not in _INDEX_SOURCE_TOOLS:
            return
        items = parse_file_list(str(raw))
        if not items:
            return
        for it in items:
            fid = _first_value(it, _ID_KEYS)
            if not fid:
                continue
            self._file_index[str(fid)] = {
                "name": str(_first_value(it, _NAME_KEYS) or ""),
                "folder": it.get("folderFlag") == 1,
                "parent": str(_first_value(it, _PARENT_KEYS) or ""),
            }

    def describe(self, tool: str, args: dict[str, Any]) -> dict[str, Any] | None:
        """把敏感操作的加密参数翻译成用户看得懂的信息（审批弹窗展示用）。

        名称来自本次会话中已见过的 list_files / search_files 结果。索引里没有的
        id 如实放进 `unresolved`，不猜测也不隐藏——审批是安全边界，宁可显示
        原始 id 也不能让用户以为自己批的是别的东西。

        没有任何可翻译内容（如纯搜索工具）时返回 None，调用方回退原始展示。
        """
        raw_ids = args.get("fileIds") or []
        if isinstance(raw_ids, str):
            raw_ids = [raw_ids]
        raw_target = args.get("targetParentId") or args.get("parentId")
        if not raw_ids and not raw_target:
            return None
        action = TOOL_ACTIONS.get(tool, tool)
        items: list[dict[str, Any]] = []
        unresolved: list[str] = []
        for fid in raw_ids:
            key = str(fid)
            info = self._file_index.get(key)
            if info and info.get("name"):
                items.append(
                    {
                        "id": key,
                        "name": info["name"],
                        "kind": "文件夹" if info.get("folder") else "文件",
                    }
                )
            else:
                unresolved.append(key)
        if items:
            names_text = "、".join(f"「{item['name']}」" for item in items)
            summary = f"{action}{names_text}"
        elif unresolved:
            summary = f"{action} {len(unresolved)} 个文件（名称待确认）"
        else:
            summary = f"{action}所选内容"

        target: dict[str, Any] | None = None
        if raw_target:
            target_id = str(raw_target)
            if target_id.strip().lower() in ROOT_MAGIC_VALUES:
                target = {"id": target_id, "name": "根目录"}
            else:
                info = self._file_index.get(target_id) or {}
                target = {"id": target_id, "name": str(info.get("name") or "")}
        return {
            "action": action,
            "summary": summary,
            "items": items,
            "target": target,
            "unresolved": unresolved,
        }

    # ------------------------------------------------------------------ 回读校验

    async def _verify_write(
        self, name: str, kwargs: dict[str, Any], result: str
    ) -> str | None:
        """写操作成功后回读目标目录，返回给模型看的校验文本。"""
        field = VERIFY_PARAMS.get(name)
        if not field or not self._verify_writes:
            return None
        if '"code":0' not in (result or ""):  # 只有成功才值得校验
            return None
        dir_id = kwargs.get(field)
        if not dir_id or not isinstance(dir_id, str):
            return None
        try:
            raw = await self._mcp.call_tool("list_files", {"parentId": dir_id}, self._user_id)
        except Exception as exc:
            logger.warning("写操作回读校验失败（不影响主结果）：%s", exc)
            return None
        self._index_result("list_files", raw)
        items = parse_file_list(str(raw))
        if items is None:
            return None
        if not items:
            return "[回读校验] 目标目录当前为空。"
        names = [
            f"{'文件夹' if it.get('folderFlag') == 1 else '文件'}"
            f"「{it.get('filename') or ''}」"
            for it in items
        ]
        return f"[回读校验] 目标目录现有 {len(items)} 项：" + "、".join(names)

    # ------------------------------------------------------------------ 工具构造

    def _to_tool(self, info: McpToolInfo) -> StructuredTool:
        async def invoke(**kwargs: Any) -> str:
            args = await self._normalize_args(info.name, dict(kwargs))
            result = await self._mcp.call_tool(info.name, args, self._user_id)
            text = str(result)
            self._index_result(info.name, text)
            note = await self._verify_write(info.name, args, text)
            return f"{text}\n{note}" if note else text

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
