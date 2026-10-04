"""Agent 侧参数治理测试：根目录 id 归一化（A2）+ 写操作回读校验（C）。

背景：list_files 的 parentId 支持 "-1" 魔法值，而 transfer/copy/create_folder
的目录参数直接进 IdUtil.decrypt()，传 "-1" 会抛 AES128Util.aesDecode fail。
模型很容易类比错，第一次写操作必然失败——本文件锁死"不再发生"这一行为。
"""
import pytest

from app.core.pan_tools import (
    McpToolFactory,
    ToolInputError,
    pick_root_id,
)
from app.mcp.client import McpToolInfo

# list_files(parentId=-1) 的真实返回形态：顶层「全部文件」的 parentId 不在列表内
ROOT_LISTING = (
    '[list_files] 云盘响应: {"code":0,"message":"SUCCESS","data":['
    '{"fileId":"jdkU-root","parentId":"DAdPa-out","filename":"全部文件",'
    '"fileSizeDesc":"--","folderFlag":1,"fileType":0},'
    '{"fileId":"Hkpu-agent","parentId":"jdkU-root","filename":"agent",'
    '"fileSizeDesc":"--","folderFlag":1,"fileType":0},'
    '{"fileId":"s31d-resume","parentId":"Hkpu-agent","filename":"李京峰-个人简历.pdf",'
    '"fileSizeDesc":"321.64 KB","folderFlag":0,"fileType":5}]}'
)
TARGET_LISTING = (
    '[list_files] 云盘响应: {"code":0,"data":['
    '{"fileId":"Hkpu-agent","parentId":"jdkU-root","filename":"agent","folderFlag":1},'
    '{"fileId":"s31d-resume","parentId":"jdkU-root",'
    '"filename":"李京峰-个人简历.pdf","folderFlag":0}]}'
)
TRANSFER_OK = '[transfer_files] 云盘响应: {"code":0}'
TRANSFER_FAIL = '[transfer_files] 云盘响应: {"code":1,"message":"AES128Util.aesDecode fail"}'


def _schema(props: dict, required: list[str]) -> dict:
    return {"type": "object", "properties": props, "required": required}


TOOLS = [
    McpToolInfo(
        name="list_files",
        description="列出目录",
        input_schema=_schema(
            {"userId": {"type": "string"}, "parentId": {"type": "string"}},
            ["userId", "parentId"],
        ),
    ),
    McpToolInfo(
        name="transfer_files",
        description="移动文件",
        input_schema=_schema(
            {
                "userId": {"type": "string"},
                "fileIds": {"type": "array", "items": {"type": "string"}},
                "targetParentId": {"type": "string"},
            },
            ["userId", "fileIds", "targetParentId"],
        ),
    ),
    McpToolInfo(
        name="search_files",
        description="搜索文件",
        input_schema=_schema(
            {"userId": {"type": "string"}, "keyword": {"type": "string"}},
            ["userId", "keyword"],
        ),
    ),
    McpToolInfo(
        name="create_folder",
        description="新建文件夹",
        input_schema=_schema(
            {
                "userId": {"type": "string"},
                "parentId": {"type": "string"},
                "folderName": {"type": "string"},
            },
            ["userId", "parentId", "folderName"],
        ),
    ),
]


class FakeMCP:
    """记录调用参数的 MCP 替身。"""

    def __init__(self, transfer_response: str = TRANSFER_OK, root_listing: str = ROOT_LISTING):
        self.calls: list[tuple[str, dict]] = []
        self.transfer_response = transfer_response
        self.root_listing = root_listing

    async def list_tools(self):
        return TOOLS

    async def call_tool(self, name, arguments, user_id):
        self.calls.append((name, dict(arguments)))
        if name == "list_files":
            # 根目录探测返回根清单；带具体 parentId 的回读返回目标清单
            if arguments.get("parentId") == "-1":
                return self.root_listing
            return TARGET_LISTING
        return self.transfer_response


async def _tool(mcp: FakeMCP, name: str):
    tools = await McpToolFactory(mcp, "42").build_all()
    return next(t for t in tools if t.name == name)


# --------------------------------------------------------------------------- 根目录解析


def test_pick_root_id_prefers_top_folder():
    """顶层文件夹：parentId 不在结果集合内。"""
    items = [
        {"fileId": "root", "parentId": "outside", "folderFlag": 1},
        {"fileId": "sub", "parentId": "root", "folderFlag": 1},
    ]
    assert pick_root_id(items) == "root"


def test_pick_root_id_falls_back_to_first_folder():
    items = [
        {"fileId": "a", "parentId": "b", "folderFlag": 0},
        {"fileId": "b", "parentId": "a", "folderFlag": 1},
    ]
    assert pick_root_id(items) == "b"


def test_pick_root_id_returns_none_without_folder():
    assert pick_root_id([{"fileId": "f1", "parentId": "x", "folderFlag": 0}]) is None


# --------------------------------------------------------------------------- A2 归一化


async def test_transfer_root_magic_value_is_normalized():
    """关键回归：targetParentId="-1" 必须在 Agent 侧换成真实根目录 id。"""
    mcp = FakeMCP()
    tool = await _tool(mcp, "transfer_files")
    await tool.ainvoke({"fileIds": ["s31d-resume"], "targetParentId": "-1"})
    transfer_call = next(c for c in mcp.calls if c[0] == "transfer_files")
    assert transfer_call[1]["targetParentId"] == "jdkU-root"


async def test_create_folder_blank_parent_is_normalized():
    mcp = FakeMCP()
    tool = await _tool(mcp, "create_folder")
    await tool.ainvoke({"parentId": "", "folderName": "备份"})
    call = next(c for c in mcp.calls if c[0] == "create_folder")
    assert call[1]["parentId"] == "jdkU-root"


async def test_real_id_is_left_untouched():
    mcp = FakeMCP()
    tool = await _tool(mcp, "transfer_files")
    await tool.ainvoke({"fileIds": ["x"], "targetParentId": "Hkpu-agent"})
    call = next(c for c in mcp.calls if c[0] == "transfer_files")
    assert call[1]["targetParentId"] == "Hkpu-agent"
    # 真实 id 无需探测根目录
    assert not any(c[0] == "list_files" and c[1]["parentId"] == "-1" for c in mcp.calls)


async def test_list_files_keeps_magic_value():
    """list_files 的 -1 是后端认可的语义，不能被改写。"""
    mcp = FakeMCP()
    tool = await _tool(mcp, "list_files")
    await tool.ainvoke({"parentId": "-1"})
    assert mcp.calls[-1] == ("list_files", {"parentId": "-1"})


async def test_root_id_is_resolved_once_per_session():
    mcp = FakeMCP()
    factory = McpToolFactory(mcp, "42")
    tools = await factory.build_all()
    transfer = next(t for t in tools if t.name == "transfer_files")
    await transfer.ainvoke({"fileIds": ["a"], "targetParentId": "-1"})
    await transfer.ainvoke({"fileIds": ["b"], "targetParentId": "ALL"})
    probes = [c for c in mcp.calls if c[0] == "list_files" and c[1].get("parentId") == "-1"]
    assert len(probes) == 1, "根目录 id 应被缓存，避免每次写操作都探测"


async def test_unresolvable_root_raises_tool_input_error():
    """拿不到根目录 id 时给出可行动的错误，而不是把 -1 原样发给后端。"""
    mcp = FakeMCP(root_listing="[list_files] 云盘响应: 服务异常")
    tool = await _tool(mcp, "transfer_files")
    with pytest.raises(ToolInputError) as exc:
        await tool.ainvoke({"fileIds": ["a"], "targetParentId": "-1"})
    assert "list_files" in str(exc.value)
    # 归一化失败就不会发出这次注定失败的 transfer
    assert not any(c[0] == "transfer_files" for c in mcp.calls)


# --------------------------------------------------------------------------- C 回读校验


async def test_verify_appends_directory_listing_on_success():
    mcp = FakeMCP(transfer_response=TRANSFER_OK)
    tool = await _tool(mcp, "transfer_files")
    out = await tool.ainvoke({"fileIds": ["s31d-resume"], "targetParentId": "jdkU-root"})
    assert "[回读校验]" in out
    assert "李京峰-个人简历.pdf" in out
    assert "agent" in out


async def test_verify_is_skipped_when_write_failed():
    mcp = FakeMCP(transfer_response=TRANSFER_FAIL)
    tool = await _tool(mcp, "transfer_files")
    out = await tool.ainvoke({"fileIds": ["s31d-resume"], "targetParentId": "jdkU-root"})
    assert "[回读校验]" not in out
    assert "aesDecode" in out
    assert not any(c[0] == "list_files" and c[1].get("parentId") == "jdkU-root" for c in mcp.calls)


async def test_verify_uses_normalized_target_dir():
    """校验要列的是归一化后的真实目录，而不是魔法值 -1。"""
    mcp = FakeMCP()
    tool = await _tool(mcp, "transfer_files")
    await tool.ainvoke({"fileIds": ["x"], "targetParentId": "-1"})
    verify_calls = [
        c for c in mcp.calls if c[0] == "list_files" and c[1].get("parentId") != "-1"
    ]
    assert verify_calls == [("list_files", {"parentId": "jdkU-root"})]


async def test_verify_can_be_disabled():
    mcp = FakeMCP()
    factory = McpToolFactory(mcp, "42", verify_writes=False)
    tools = await factory.build_all()
    transfer = next(t for t in tools if t.name == "transfer_files")
    out = await transfer.ainvoke({"fileIds": ["x"], "targetParentId": "jdkU-root"})
    assert "[回读校验]" not in out


async def test_verify_failure_does_not_break_tool_result():
    """回读本身异常时静默跳过，主结果照常返回。"""
    class BrokenMCP(FakeMCP):
        async def call_tool(self, name, arguments, user_id):
            self.calls.append((name, dict(arguments)))
            if name == "list_files" and arguments.get("parentId") != "-1":
                raise RuntimeError("回读失败")
            return await super().call_tool(name, arguments, user_id)

    mcp = BrokenMCP()
    tool = await _tool(mcp, "transfer_files")
    out = await tool.ainvoke({"fileIds": ["x"], "targetParentId": "jdkU-root"})
    assert '{"code":0}' in out
    assert "[回读校验]" not in out


# --------------------------------------------------------------------------- 审批可读化


class SearchFakeMCP(FakeMCP):
    """search_files 返回带 fileName 字段的 JSON（与 list_files 的 filename 不同）。"""

    async def call_tool(self, name, arguments, user_id):
        if name == "search_files":
            self.calls.append((name, dict(arguments)))
            return (
                '[search_files] 云盘响应: {"code":0,"data":['
                '{"fileId":"s31d-resume","parentId":"Hkpu-agent",'
                '"fileName":"李京峰-个人简历.pdf","folderFlag":0}]}'
            )
        return await super().call_tool(name, arguments, user_id)


async def _factory_after_listing(mcp: FakeMCP) -> McpToolFactory:
    """先跑一次 list_files，让工厂建立 fileId → 名称索引（模拟真实对话顺序）。"""
    factory = McpToolFactory(mcp, "42")
    tools = await factory.build_all()
    lister = next(t for t in tools if t.name == "list_files")
    await lister.ainvoke({"parentId": "-1"})
    return factory


async def test_describe_translates_file_ids_to_names():
    factory = await _factory_after_listing(FakeMCP())
    display = factory.describe(
        "transfer_files", {"fileIds": ["s31d-resume"], "targetParentId": "jdkU-root"}
    )
    assert display["summary"] == "移动「李京峰-个人简历.pdf」"
    assert display["target"] == {"id": "jdkU-root", "name": "全部文件"}
    assert display["items"][0]["kind"] == "文件"


async def test_describe_marks_unresolved_ids_without_guessing():
    """索引里没有的 id 如实暴露，绝不猜测名称（审批是安全边界）。"""
    factory = await _factory_after_listing(FakeMCP())
    display = factory.describe("delete_file", {"fileIds": ["unknown-id"]})
    assert display["items"] == []
    assert display["unresolved"] == ["unknown-id"]
    assert "1 个文件" in display["summary"]


async def test_describe_treats_root_magic_value_readably():
    factory = McpToolFactory(FakeMCP(), "42")
    display = factory.describe("create_folder", {"parentId": "-1", "folderName": "备份"})
    assert display["target"]["name"] == "根目录"


async def test_describe_returns_none_when_nothing_to_translate():
    """纯搜索类工具没有可翻译参数 → 返回 None，由前端回退原始展示。"""
    factory = McpToolFactory(FakeMCP(), "42")
    assert factory.describe("search_files", {"keyword": "简历"}) is None


async def test_search_files_fileName_field_is_indexed():
    """字段名差异（list_files 用 filename / search_files 用 fileName）都要能索引。"""
    factory = McpToolFactory(SearchFakeMCP(), "42")
    tools = await factory.build_all()
    search = next(t for t in tools if t.name == "search_files")
    await search.ainvoke({"keyword": "简历"})
    display = factory.describe("delete_file", {"fileIds": ["s31d-resume"]})
    assert display["items"][0]["name"] == "李京峰-个人简历.pdf"
