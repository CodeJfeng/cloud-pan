# LangGraph 智能体编排层迁移方案

> **给执行 Agent 的说明：** REQUIRED SUB-SKILL: 使用 superpowers:executing-plans 逐任务实现本计划。代码示例给出的是骨架/关键逻辑，执行时以现有代码 + 官方 API 校准补全，务必保持每个任务测试先红后绿再提交。

**Goal:** 把 `agent/`（Python FastAPI 云盘智能助手）的手写 function-calling 循环迁移到 **LangGraph** 显式状态图，支持 ReAct 与 Plan-and-Execute（Planner-Worker-Collector）双范式路由、并行子任务执行、MySQL Checkpointer 断点与 interrupt 人工确认，同时保持对外 SSE 契约、MySQL 会话/trace 表、Java MCP Server 与前端页面完全不变。

**Architecture:** 一条由用户消息进入的 `StateGraph`：`router` 节点判断任务复杂度（简单→ReAct 路径，复杂→Planner 路径）；ReAct 路径保留现有单 agent 多轮工具调用能力（含"承诺搜索强制兜底" nudge、敏感操作确认）；Planner 路径由 LLM 产出相互独立的子任务，经 `Send()` 扇出到并发 Worker（按工具域垂直约束），Collector 汇总结果后由 Summarizer 流式输出最终回答。全部节点通过自定义事件流（`get_stream_writer`）按现有 SSE 事件名发出 `thinking/llm_decision/tool_start/tool_end/confirmation_required/delta/done/error`；敏感操作在节点内调用 `interrupt()`，由 API 层基于 `__interrupt__` + `Command(resume=...)` 实现"挂起 SSE → 等待 `/confirm` → 恢复同一 thread"的人工确认闭环。

**Tech Stack:** Python 3.11+、LangGraph（含 `langgraph`、`langchain-core`）、`langchain-openai`（OpenAI 兼容网关接 DeepSeek/通义/Kimi）、`langgraph-checkpoint-mysql[aiomysql]`（AIOMySQLSaver）、FastAPI、httpx（保留手写 MCP 客户端）、pytest。

---

## 前置假设与确认结果

| 项 | 决策 |
|---|---|
| 改造范围 | 仅 `agent/` Python 编排层；Java MCP Server、云盘后端、前端页面与 SSE 事件名全部不动 |
| 双范式 | 简单请求走 ReAct，复杂请求走 Planner（路由节点先行判定） |
| Worker | 相互独立的子任务可**并发**执行（LangGraph `Send` 扇出），有依赖的操作合并进同一子任务由单个 Worker 内部串行完成 |
| 持久化 | LangGraph Checkpointer 管执行状态（MySQL，跨重启可断点），现有 `r_pan_agent_*` 会话/消息/trace 表继续管会话列表、展示与审计（双写并存） |
| 工具接入 | 保留 Java MCP Server；现有手写 `MCPClient` 包装为 LangGraph `BaseTool`，不引入官方 mcp SDK |
| 人工确认 | 现有 `/confirm` 端点形态不变；内部实现从"asyncio.Future 阻塞"迁移为 LangGraph `interrupt()` + `Command(resume)` 恢复同一 thread 执行 |

**迁移不变量（必须有回归测试守住）：**
- SSE 事件名集合：`thinking / llm_decision / confirmation_required / tool_start / tool_end / delta / done / error`（`done` 事件带 `sessionId`/`traceId`，`tool_end` 带 `duration_ms`/`resultPreview`，跳过执行时带 `skipped`）。
- `POST /api/agent/chat`、`POST /api/agent/confirm`、`GET /api/agent/sessions`、`GET /api/agent/session/{id}/messages`、`GET /api/agent/session/{id}/traces`、`GET /api/agent/trace/{id}`、`GET /api/agent/health` 行为一致。
- `Agent` 注入参数 `userId` 不出现在交给 LLM 的 tool schema 中，执行时自动注入。
- 提示词场景路由（search/file_ops/share/default）与目录快照注入行为保留。

---

## 迁移后模块结构

```
agent/app/
├── main.py                      # 不变（仅路由注册）
├── config.py                    # 修改：新增 checkpoint/LLM 配置项
├── api/
│   └── chat.py                  # 重写内部：改用 graph runner；端点签名与返回不变
├── core/
│   ├── agent.py                 # 删除（原手写循环，逻辑迁移至 nodes/graph）
│   ├── llm.py                   # 删除（由 core/model.py 的 ChatOpenAI 取代）
│   ├── model.py                 # 新增：build_chat_model(settings) → ChatOpenAI
│   ├── prompts.py               # 修改：新增 planner/worker/collector 系统提示词
│   ├── context.py               # 新增：classify_intent/目录快照/系统提示词组装（自 agent.py 抽取）
│   ├── pan_tools.py             # 新增：MCPClient → LangChain BaseTool 工厂 + 工具域分组
│   ├── state.py                 # 新增：AgentState + 各 channel reducer
│   ├── events.py                # 新增：get_stream_writer 事件发射助手（SSE 契约映射）
│   ├── checkpoint.py            # 新增：MySQL/内存 checkpointer 工厂
│   ├── runner.py                # 新增：graph astream + interrupt/resume 循环 → SSE 事件
│   ├── graph.py                 # 新增：build_agent_graph（拓扑 + 编译）
│   └── nodes/
│       ├── react.py             # 新增：react_agent / react_tools / 条件边
│       └── plan.py              # 新增：router/planner/worker/collector/summarizer
├── mcp/client.py                # 不变（手写 Streamable HTTP 客户端）
└── storage/session_store.py     # 不变（会话/消息/trace 照旧）
```

依赖新增（`agent/requirements.txt`）：

```
langgraph==<实施时最新稳定版>
langchain-core==<与 langgraph 兼容的版本>
langchain-openai==<兼容版本>
langgraph-checkpoint-mysql[aiomysql]
```

> 装包后先跑 `python -c "import langgraph, langgraph.checkpoint.mysql.aio; print(langgraph.__version__)"` 确认可导入，README 驱动选择参考 `github.com/tjni/langgraph-checkpoint-mysql`（提供 `langgraph.checkpoint.mysql.aio.AIOMySQLSaver`）。

---

## 任务 1：依赖、配置项与最小模型适配

**文件：**
- 修改：`agent/requirements.txt`
- 修改：`agent/app/config.py`
- 修改：`agent/tests/test_config.py`

**Step 1：先写失败测试**

`test_config.py` 追加：

```python
def test_checkpoint_and_llm_config_defaults():
    s = Settings()
    assert s.mysql_checkpoint_url == "mysql://root:123456@127.0.0.1:3306/cloud_drive_pan?charset=utf8mb4"
    assert s.llm_temperature == 0.3
    assert s.graph_recursion_limit == 50
    assert s.worker_max_iterations == 3
```

**Step 2：跑测试确认失败**

```bash
cd d:/Project/cloud-pan/agent
.\.venv\Scripts\python.exe -m pytest tests/test_config.py -v
```

预期：`AttributeError`（新字段不存在）。

**Step 3：实现**

`requirements.txt` 追加上述 4 个包（版本取实施时兼容组合，锁定精确版本）。

`config.py` `Settings` 增加：

```python
    # LangGraph 执行状态 Checkpointer（MySQL，与业务库同实例；值不含 aiomysql 驱动前缀）
    mysql_checkpoint_url: str = "mysql://root:123456@127.0.0.1:3306/cloud_drive_pan?charset=utf8mb4"
    llm_temperature: float = 0.3
    graph_recursion_limit: int = 50
    worker_max_iterations: int = 3
    react_max_iterations: int = 8
```

**Step 4：跑测试确认通过**

预期：PASS。

**Step 5：提交**

```bash
git add agent/
git commit -m "feat(agent): add langgraph deps and checkpoint config"
```

---

## 任务 2：LangChain 模型适配器

**文件：**
- 创建：`agent/app/core/model.py`
- 创建：`agent/tests/test_model.py`

**Step 1：先写失败测试**

```python
from unittest.mock import patch

from app.config import Settings
from app.core.model import build_chat_model


def test_build_chat_model_uses_openai_compatible_settings():
    with patch("app.core.model.ChatOpenAI") as MockChat:
        build_chat_model(Settings())
        kwargs = MockChat.call_args.kwargs
        assert kwargs["base_url"].rstrip("/") == "https://api.deepseek.com/v1"
        assert kwargs["api_key"] == ""
        assert kwargs["model"] == "deepseek-chat"
        assert kwargs["temperature"] == 0.3


def test_build_chat_model_requires_api_key():
    from app.core.model import build_chat_model
    from app.core.llm import LLMError  # 复用既有异常类型或新建
    # 若 api_key 为空则抛错（行为与旧 LLMClient 一致）
```

**Step 2：跑测试确认失败**

预期：`ModuleNotFoundError: No module named 'app.core.model'`。

**Step 3：实现**

`app/core/model.py`：

```python
"""LangChain ChatOpenAI 适配：兼容任意 OpenAI 兼容网关（DeepSeek/通义/Kimi）。"""
from langchain_openai import ChatOpenAI

from app.config import Settings


def build_chat_model(settings: Settings) -> ChatOpenAI:
    """按 settings 构建 ChatOpenAI；api_key 缺失时抛错并给出环境变量提示。"""
    if not (settings.llm_api_key or "").strip():
        from app.core.llm import LLMError
        raise LLMError(
            "未配置 LLM API Key：请设置 AGENT_LLM_API_KEY（并确认 AGENT_LLM_BASE_URL / AGENT_LLM_MODEL）后重启服务"
        )
    return ChatOpenAI(
        model=settings.llm_model,
        base_url=settings.llm_base_url,
        api_key=settings.llm_api_key,
        temperature=settings.llm_temperature,
        streaming=True,
    )
```

> 说明：若 api_key 校验放 test 依赖处更易测，可改为在 runner 首跑时校验（见任务 10），此处以测试可过为准微调。

**Step 4：跑测试确认通过** → PASS。

**Step 5：提交**

```bash
git add agent/
git commit -m "feat(agent): add langchain chat model adapter"
```

---

## 任务 3：MCP 工具 → LangGraph Tool 工厂

把 `app/mcp/client.py` 的 `McpToolInfo` 转换为 LangChain `BaseTool`，执行时从 `config` 读 `user_id` 自动注入；schema 剔除 `userId`；按工具名标记"敏感"与"所属 Worker 域"。

**文件：**
- 创建：`agent/app/core/pan_tools.py`
- 创建：`agent/tests/test_pan_tools.py`

**Step 1：先写失败测试**

```python
import json
from types import SimpleNamespace

import pytest
from langchain_core.tools import BaseTool

from app.core.pan_tools import (
    SENSITIVE_TOOLS,
    TOOL_GROUP,
    McpToolFactory,
    tool_group,
)
from app.mcp.client import McpToolInfo

MCP_TOOLS = [
    McpToolInfo(name="search_files", description="搜索文件",
                input_schema={"type": "object",
                              "properties": {"userId": {"type": "string"}, "keyword": {"type": "string"}},
                              "required": ["userId", "keyword"]}),
    McpToolInfo(name="delete_file", description="删除（敏感）",
                input_schema={"type": "object",
                              "properties": {"fileIds": {"type": "array", "items": {"type": "string"}}},
                              "required": ["fileIds"]}),
]


@pytest.mark.asyncio
async def test_tool_schema_drops_user_id_and_injects_on_call():
    calls = []

    async def fake_call(name, arguments, user_id):
        calls.append((name, arguments, user_id))
        return "ok"

    factory = McpToolFactory(list_tools=lambda: MCP_TOOLS, call_tool=fake_call)
    tools = factory.build_all()

    assert len(tools) == 2
    # schema 不含 userId
    schema = tools[0].args_schema.model_json_schema()
    assert "userId" not in schema["properties"]
    assert "userId" not in schema.get("required", [])
    # 调用时注入 config 中的 user_id
    from langchain_core.messages import AIMessage, ToolMessage
    result = await tools[0].ainvoke({"keyword": "报告"})
    assert calls == [("search_files", {"keyword": "报告"}, "u_1")]


def test_sensitive_flag_and_grouping():
    assert SENSITIVE_TOOLS == frozenset({"transfer_files", "delete_file"})
    assert tool_group("delete_file") == "file_ops"
    assert tool_group("create_share") == "share"
```

> 执行时若从 config 读取 user_id 的细节使单测变复杂，可采用"factory 构造时绑定 user_id"的更简单签名，以测试可过为准，但保留注入点清晰可测。

**Step 2：跑测试确认失败**

预期：`ModuleNotFoundError`。

**Step 3：实现**

`app/core/pan_tools.py` 关键逻辑：

```python
"""把 MCP 工具转成 LangGraph 可绑定的 LangChain BaseTool。"""
import json
from typing import Any, Awaitable, Callable, Iterable

from langchain_core.tools import StructuredTool
from pydantic import BaseModel, create_model

from app.config import settings
from app.mcp.client import MCPClient, McpToolInfo

SENSITIVE_TOOLS = frozenset({"transfer_files", "delete_file"})

# Worker 垂直分工：子任务 worker_group -> 允许的工具名集合（均含 list_files 作为定位辅助）
TOOL_GROUP: dict[str, frozenset[str]] = {
    "search": frozenset({"search_files", "list_files"}),
    "file_ops": frozenset({"list_files", "create_folder", "rename_file", "delete_file",
                           "transfer_files", "copy_files", "list_recycles", "restore_recycle"}),
    "share": frozenset({"list_files", "create_share"}),
    "recycle": frozenset({"list_recycles", "restore_recycle", "list_files"}),
}
ALL_TOOLS = frozenset().union(*TOOL_GROUP.values())


def tool_group(tool_name: str) -> str:
    for group, names in TOOL_GROUP.items():
        if tool_name in names:
            return group
    return "search"


def _py_type(prop: dict[str, Any]) -> Any:
    t = prop.get("type")
    if t == "integer":
        return int
    if t == "array":
        items = prop.get("items", {})
        return list[_py_type(items)] if items.get("type") else list
    return str


def _args_model(name: str, input_schema: dict[str, Any]) -> type[BaseModel]:
    props = input_schema.get("properties", {})
    fields = {}
    for field, prop in props.items():
        if field == "userId":
            continue  # 内部注入参数
        fields[field] = (_py_type(prop), prop.get("description", ""))
    return create_model(f"{name}Args", **fields)


class McpToolFactory:
    """每次 MCP 会话创建一次；工具闭包执行时注入 userId。"""

    def __init__(self, mcp: MCPClient, user_id: str):
        self._mcp = mcp
        self._user_id = user_id

    async def _run(self, name: str, **kwargs: Any) -> str:
        return await self._mcp.call_tool(name, kwargs, self._user_id)

    def _to_tool(self, info: McpToolInfo) -> StructuredTool:
        async def invoke(**kwargs: Any) -> str:
            return await self._run(info.name, **kwargs)

        return StructuredTool.from_function(
            coroutine=invoke,
            name=info.name,
            description=info.description or "",
            args_schema=_args_model(info.name, info.input_schema),
        )

    def build(self, names: Iterable[str] | None = None) -> list[StructuredTool]:
        allow = set(names) if names is not None else set(ALL_TOOLS)
        return [self._to_tool(t) for t in self._mcp_tools if t.name in allow]
```

> 并发 Worker 复用同一个 `MCPClient`：httpx `AsyncClient` 支持并发，但 Streamable HTTP 会话对并发 JSON-RPC 的兼容性需在任务 9 联调验证；若发现串扰，则改为"每 Worker 懒建独立短连接"（`MCPClient.connect/close` 已支持），方案预留。

**Step 4：跑测试确认通过** → PASS。

**Step 5：提交**

```bash
git add agent/
git commit -m "feat(agent): wrap mcp tools as langgraph tools"
```

---

## 任务 4：提示词扩展与上下文工具函数抽取

**文件：**
- 修改：`agent/app/core/prompts.py`
- 创建：`agent/app/core/context.py`
- 创建：`agent/tests/test_context.py`
- 后续在任务 5 删除 `agent/app/core/agent.py`（迁移其纯函数）

**Step 1：先写失败测试**

```python
from app.core.context import classify_intent
from app.core.prompts import COLLECTOR_SYSTEM_PROMPT, PLANNER_SYSTEM_PROMPT, WORKER_SYSTEM_PROMPT


@pytest.mark.parametrize("message,expected", [
    ("帮我搜索李京峰简历", "search"),
    ("把这个文件夹分享给同事", "share"),
    ("新建一个文件夹", "file_ops"),
    ("你好", "default"),
])
def test_classify_intent(message, expected):
    assert classify_intent(message) == expected


def test_planner_worker_collector_prompts_present():
    assert "子任务" in PLANNER_SYSTEM_PROMPT
    assert "独立" in PLANNER_SYSTEM_PROMPT
    assert WORKER_SYSTEM_PROMPT and COLLECTOR_SYSTEM_PROMPT
```

**Step 2：跑测试确认失败** → `ModuleNotFoundError`。

**Step 3：实现**

把 `agent.py` 中纯函数（`classify_intent`、`_parse_file_list`、`_format_directory_context`、`_PROMISED_CONTINUE_PATTERN`、`_looks_like_promised_search`、`resolve_system_prompt`）原样迁入 `app/core/context.py`（不依赖 LLM/MCP 的纯逻辑；目录快照的"拉取"逻辑保留在 runner 预置阶段）。

`prompts.py` 追加（在既有 `SCENARIO_PROMPTS` 之上，复用 `BASE_PROMPT` 中的回答风格约束）：

```python
PLANNER_SYSTEM_PROMPT = f"""{BASE_PROMPT}

【当前角色：任务规划器】
用户请求可能包含多个操作。请把目标拆解为若干【相互独立】的子任务，规则：
1. 每个子任务只干一件事，彼此无数据依赖；若存在依赖（先查后动），把整条链并进同一个子任务的描述里。
2. 每个子任务必须自包含：给出足够的操作说明，执行者无需跨子任务取结果。
3. 标注子任务的类型：search（搜索定位）/ file_ops（文件操作）/ share（分享）/ recycle（回收站）。
4. 若子任务涉及删除或移动，必须把 fileId/目标说清楚，执行阶段会再向用户确认。
5. 单步请求（一个操作）不需要拆，直接原样作为唯一子任务。"""
```

并给出结构相同、面向 Worker 执行者与 Collector 汇总者的 `WORKER_SYSTEM_PROMPT`（含"每步只调用本组工具、失败如实上报、不编造"）与 `COLLECTOR_SYSTEM_PROMPT`（含"汇总各子任务结果，检查有无遗漏或失败，供最终回答使用"）。

> Worker 提示词按子任务类型拼接：`WORKER_SYSTEM_PROMPT + "\n" + SCENARIO_PROMPTS[scenario]`，复用既有场景规则。

**Step 4：跑测试确认通过** → PASS。

**Step 5：提交**

```bash
git add agent/
git commit -m "feat(agent): extract context helpers and add planner prompts"
```

---

## 任务 5：AgentState / 事件发射 / Checkpointer

**文件：**
- 创建：`agent/app/core/state.py`
- 创建：`agent/app/core/events.py`
- 创建：`agent/app/core/checkpoint.py`
- 创建：`agent/tests/test_state.py`
- 删除：`agent/app/core/agent.py`、`agent/tests/test_agent.py`（旧循环测试被后续任务取代）

**Step 1：先写失败测试**

```python
from langchain_core.messages import AIMessage, HumanMessage
from langgraph.checkpoint.memory import MemorySaver

from app.core.checkpoint import build_checkpointer
from app.core.events import SSE_TYPES, emit
from app.core.state import AgentState


def test_state_keys_exist():
    required = {"messages", "mode", "user_input", "steps", "results", "final_answer", "error"}
    assert required.issubset(AgentState.__annotations__)


def test_events_contract():
    # 对外 SSE 事件名集合保持不变
    assert SSE_TYPES == {"thinking", "llm_decision", "confirmation_required",
                         "tool_start", "tool_end", "delta", "done", "error"}


def test_checkpointer_default_is_memory_in_test():
    cp = build_checkpointer(force_memory=True)
    assert isinstance(cp, MemorySaver)
```

**Step 2：跑测试确认失败** → `ModuleNotFoundError`。

**Step 3：实现**

`state.py`：

```python
"""LangGraph AgentState。messages 用 add_messages 归约，结果按 step_id 累加。"""
import operator
from typing import Annotated, TypedDict

from langchain_core.messages import BaseMessage
from langgraph.graph.message import add_messages


def add_results(left: list[dict], right: list[dict]) -> list[dict]:
    out = {r["step_id"]: r for r in left}
    for r in right:
        out[r["step_id"]] = r
    return list(out.values())


class AgentState(TypedDict):
    messages: Annotated[list[BaseMessage], add_messages]  # 完整对话上下文（含系统提示词）
    user_input: str
    mode: str                      # "react" | "plan"
    steps: list[dict]              # planner 输出的子任务
    results: Annotated[list[dict], add_results]  # {step_id,status,result}*
    final_answer: str
    error: str
    retries: int
```

`events.py`：

```python
"""节点内自定义事件发射；runner 侧映射为对外 SSE 事件。"""
from langgraph.config import get_stream_writer

SSE_TYPES = frozenset({"thinking", "llm_decision", "confirmation_required",
                       "tool_start", "tool_end", "delta", "done", "error"})


def emit(**data) -> None:
    """向 runner 的自定义流通道发事件（type 须为 SSE_TYPES 之一）。"""
    assert data.get("type") in SSE_TYPES, data.get("type")
    get_stream_writer().emit(data)
```

`checkpoint.py`：

```python
"""Checkpointer 工厂：测试用 MemorySaver，生产用 MySQL(AIOMySQLSaver)。"""
from typing import Any

from langgraph.checkpoint.memory import MemorySaver


def build_checkpointer(mysql_url: str | None = None,
                       force_memory: bool = False) -> Any:
    if force_memory:
        return MemorySaver()
    from langgraph.checkpoint.mysql.aio import AIOMySQLSaver
    return AIOMySQLSaver.from_conn_string(mysql_url or settings.mysql_checkpoint_url)
```

> 内存版仅为测试/无库环境降级；生产路径为 MySQL。AIOMySQLSaver 首次使用需 `await checkpointer.setup()`（建 `checkpoints` 等表），在 runner 启动时执行一次。同步细节按 aiomysql 驱动文档校准。

**Step 4：跑测试确认通过** → PASS。

**Step 5：提交**

```bash
git add agent/
git rm agent/app/core/agent.py agent/tests/test_agent.py
git commit -m "feat(agent): add state, events and checkpointer factories"
```

---

## 任务 6：ReAct 范式节点（含 nudge、确认、错误回填）

**文件：**
- 创建：`agent/app/core/nodes/__init__.py`
- 创建：`agent/app/core/nodes/react.py`
- 创建：`agent/tests/test_react_nodes.py`
- 创建：`agent/tests/helpers.py`（ScriptedChatModel 等测试替身）

**Step 1：先写失败测试（节点级）**

`tests/helpers.py` 提供可脚本化的最小模型替身（实现节点只依赖的接口：`ainvoke(messages)` / `astream(messages)`，返回/产出 `AIMessage` 与增量），例如按脚本依次返回 `AIMessage(content="", tool_calls=[...])` 或 `AIMessage(content="找到了文件")`。

```python
@pytest.mark.asyncio
async def test_react_agent_emits_llm_decision_and_routes_to_tools():
    # 脚本：第一次返回 tool_call(search_files)，第二次返回最终文本
    # 用 get_stream_writer 捕获 emit 事件
    # 断言：llm_decision 事件 tools==["search_files"]；返回后 messages 含 assistant+tool 消息
```

再覆盖三个回归点（等价旧行为）：
- 工具异常不会中断：工具抛错 → 生成 `ToolMessage` 错误文本 → 下一轮 LLM 正常收尾（对应 `test_tool_error_is_fed_back`）。
- 承诺搜索 nudge：模型无工具调用但内容命中承诺句式且轮次未超限 → 注入强制搜索的用户消息并回到 agent（对应 `test_stream_run_nudges_promised_search`）。
- 敏感工具不直接执行：调用转移给 `confirm_tool_execution` 注入函数处理（本任务先以"可注入的确认器回调"实现，interrupt 在任务 8 接入）。

**Step 2：跑测试确认失败** → `ModuleNotFoundError`。

**Step 3：实现**

`nodes/react.py` 结构（节点签名统一为 `async def node(state, config)`，从 `config` 读取注入依赖）：

```python
"""ReAct 路径：agent 节点 <-> tools 节点循环，条件边收尾。"""
import json
import time
from typing import Any

from langchain_core.messages import AIMessage, ToolMessage

from app.core.context import _looks_like_promised_search
from app.core.events import emit
from app.core.state import AgentState


class ReactDeps:
    """react 路径运行时依赖（节点经 graph 构造闭包注入，便于测试替换）。"""
    def __init__(self, model, tools_by_name: dict, confirm: Any):
        self.model = model
        self.tools_by_name = tools_by_name
        self.confirm = confirm  # async (tool_name, args, request_id) -> bool


async def react_agent(state, deps: "ReactDeps") -> dict:
    """调模型做一次决策。产生 tool_calls → 记录 llm_decision 事件并交 tools 节点；
    无工具调用 → 记录最终答案并走向结束（delta 流由 runner 在 Summarizer 段补齐）。"""
    emit(type="thinking", content="正在分析你的需求…")
    t0 = time.perf_counter()
    resp: AIMessage = await deps.model.ainvoke(state["messages"])
    llm_ms = int((time.perf_counter() - t0) * 1000)
    if resp.tool_calls:
        emit(type="llm_decision", tools=[c["name"] for c in resp.tool_calls],
             duration_ms=llm_ms)
        return {"messages": [resp], "mode": "react"}
    # 无工具调用
    answer = resp.content if isinstance(resp.content, str) else ""
    emit(type="llm_decision", tools=[], duration_ms=llm_ms)
    return {"messages": [resp], "final_answer": answer}


async def react_tools(state, deps: "ReactDeps") -> dict:
    """执行最近一条 assistant 消息中的全部 tool_calls，结果回填为 ToolMessage。"""
    msgs = []
    last_ai = next((m for m in reversed(state["messages"])
                    if isinstance(m, AIMessage) and m.tool_calls), None)
    for tc in last_ai.tool_calls:
        name, args = tc["name"], tc.get("args", {})
        # 敏感工具确认（任务 8 接入 interrupt；本任务由注入的 confirm 实现）
        if name in deps.sensitive and deps.confirm is not None:
            request_id = uuid.uuid4().hex
            emit(type="confirmation_required", requestId=request_id,
                 tool=name, arguments=args)
            approved = await deps.confirm(name, args, request_id)
            if not approved:
                msgs.append(ToolMessage(content="用户拒绝了该操作，未执行。",
                                        tool_call_id=tc["id"]))
                continue
        emit(type="tool_start", tool=name, arguments=args)
        t0 = time.perf_counter()
        try:
            result = await deps.tools_by_name[name].ainvoke(args)
            msgs.append(ToolMessage(content=str(result), tool_call_id=tc["id"]))
            emit(type="tool_end", tool=name, result=str(result),
                 duration_ms=int((time.perf_counter() - t0) * 1000))
        except Exception as exc:
            text = f"工具执行异常: {exc}"
            msgs.append(ToolMessage(content=text, tool_call_id=tc["id"]))
            emit(type="tool_end", tool=name, result=text,
                 duration_ms=int((time.perf_counter() - t0) * 1000), error=True)
    return {"messages": msgs}
```

条件边（写在 `graph.py` 任务 7，先在本文件提供纯函数便于测试）：

```python
def react_route(state) -> str:
    last = state["messages"][-1]
    if isinstance(last, AIMessage) and last.tool_calls:
        return "tools"
    # 承诺搜索兜底（nudge）
    if (not last.tool_calls and isinstance(last, AIMessage)
            and isinstance(last.content, str) and _looks_like_promised_search(last.content)
            and state.get("retries", 0) < 2):
        return "nudge"
    return "end"
```

> 模型调用统一走 `ainvoke`；最终答案的逐字 delta 由 Summarizer/runner 统一处理（任务 7/8），本节点不做二次流式，先保证决策正确。

**Step 4：跑测试确认通过** → PASS。

**Step 5：提交**

```bash
git add agent/
git commit -m "feat(agent): add react nodes with nudge and confirmation hooks"
```

---

## 任务 7：图组装（骨架：react + 条件边）+ 消息历史转换

**文件：**
- 创建：`agent/app/core/graph.py`
- 创建：`agent/tests/test_graph_react.py`

**Step 1：先写失败测试（整图级）**

```python
from langchain_core.messages import HumanMessage
from langgraph.checkpoint.memory import MemorySaver
from langgraph.graph import END, StateGraph

from app.core.graph import build_agent_graph
from app.core.state import AgentState
from app.core.checkpoint import build_checkpointer
# helpers 提供 ScriptedModel（脚本驱动 ainvoke）

@pytest.mark.asyncio
async def test_react_simple_flow_no_tool():
    # 脚本：直接最终文本
    graph = build_agent_graph(model=ScriptedModel([{"content": "你好呀"}]), ...,
                              checkpointer=build_checkpointer(force_memory=True))
    out = await graph.ainvoke({"messages": [HumanMessage("你好")], "user_input": "你好"},
                              {"configurable": {"thread_id": "t1"}})
    assert out["final_answer"] == "你好呀"
```

再补：单工具调用闭环（LLM→工具→LLM 收尾）在图级可跑通、tool schema 已剥离 userId。

**Step 2：跑测试确认失败** → `ModuleNotFoundError`。

**Step 3：实现**

`graph.py` 骨架（本轮先接 react 路径；router/plan 分支后续任务接入，接口不变）：

```python
"""LangGraph 状态图组装与编译。"""
from langgraph.graph import END, START, StateGraph
from langgraph.graph.state import CompiledStateGraph

from app.core.state import AgentState


def build_agent_graph(*, model, mcp, user_id: str, checkpointer,
                      confirm=None, force_memory: bool = False) -> CompiledStateGraph:
    """组装整图。react 路径先行，后续任务逐步挂 router/plan 节点。

    model/mcp 由调用方注入（chat 层单例，测试可替换为脚本替身）。
    """
    from app.core.nodes import react
    from app.core.pan_tools import McpToolFactory

    factory = McpToolFactory(mcp, user_id)
    tools = {t.name: t for t in factory.build()}
    deps = react.ReactDeps(model=model, tools_by_name=tools, confirm=confirm)

    g = StateGraph(AgentState)
    g.add_node("react_agent", lambda s: react.react_agent(s, deps))
    g.add_node("react_tools", lambda s: react.react_tools(s, deps))
    g.add_edge(START, "react_agent")
    g.add_conditional_edges(
        "react_agent",
        react.react_route,
        {"tools": "react_tools", "nudge": "react_agent", "end": END},
    )
    g.add_edge("react_tools", "react_agent")
    return g.compile(checkpointer=checkpointer)
```

> 注意：`interrupt()`（任务 8）要求编译时带 checkpointer；MemorySaver/MySQL 均可。工具/节点若在后续任务需要从 `config.configurable` 读用户态，签名保持一致即可。

**Step 4：跑测试确认通过** → PASS。

**Step 5：提交**

```bash
git add agent/
git commit -m "feat(agent): assemble langgraph with react path"
```

---

## 任务 8：runner —— graph 流式驱动 + interrupt 恢复 + SSE 事件映射

**文件：**
- 创建：`agent/app/core/runner.py`
- 创建：`agent/tests/test_runner_sse.py`

这是迁移核心：把"节点自定义事件 + `__interrupt__`"翻译成现有 SSE 事件流；`/confirm` 挂起/恢复闭环。

**Step 1：先写失败测试**

基于任务 6 的脚本模型 + 脚本工具替身，构造一次"敏感工具触发确认"的完整跑动：

```python
@pytest.mark.asyncio
async def test_runner_interrupt_then_resume():
    # 模型脚本：delete_file → 最终文本
    graph = build_agent_graph(...)   # confirm 阶段使用真实 interrupt 路径
    events: list[dict] = []
    async for ev in run_agent_stream(graph, inputs=..., thread_id="t1", user_id="42"):
        events.append(ev)
        if ev["type"] == "confirmation_required":
            # 模拟用户稍后批准：向注册表 future 置 True，runner 内部恢复
            pass  # 详见 runner 设计（事件循环会 await 恢复）
    assert any(e["type"] == "tool_start" for e in events)   # 批准后才真正执行
    assert events[-1]["type"] == "done"
```

**Step 2：跑测试确认失败** → `ModuleNotFoundError`。

**Step 3：实现**

`runner.py` 关键逻辑：

```python
"""Graph → SSE 事件适配层。维护确认注册表；interrupt 挂起、Command(resume) 恢复同一 thread。"""
import asyncio
from typing import Any, AsyncIterator

from langgraph.types import Command

CONFIRM_TIMEOUT_SECONDS = 120
# request_id -> (user_id, asyncio.Future[bool])，兼容现有 /confirm 语义
_pending_confirmations: dict[str, tuple[str, asyncio.Future[bool]]] = {}


async def run_agent_stream(*, graph, inputs: dict, config: dict,
                           user_id: str) -> AsyncIterator[dict[str, Any]]:
    """按 thread 驱动 graph：正常产出即转发；遇到 __interrupt__ 则挂起等待用户批复后恢复。"""
    loop = asyncio.get_running_loop()
    first = True
    while True:
        payload = inputs if first else Command(resume=approved)
        first = False
        interrupted = None
        async for chunk in graph.astream(payload, config=config,
                                         stream_mode=["custom", "updates"]):
            if isinstance(chunk, dict) and "__interrupt__" in chunk:
                interrupted = chunk["__interrupt__"][0]  # Interrupt(value)
                break
            # custom 模式下 chunk 为节点 emit 的事件 dict，直接转发
            if isinstance(chunk, dict) and chunk.get("type"):
                yield chunk
        if interrupted is None:
            return
        value = interrupted.value           # {"tool": name, "arguments": args, "requestId": rid}
        rid = value["requestId"]
        fut: asyncio.Future[bool] = loop.create_future()
        _pending_confirmations[rid] = (user_id, fut)
        yield {"type": "confirmation_required", "requestId": rid,
               "tool": value["tool"], "arguments": value["arguments"]}
        try:
            approved = await asyncio.wait_for(fut, timeout=CONFIRM_TIMEOUT_SECONDS)
        except asyncio.TimeoutError:
            approved = False
        finally:
            _pending_confirmations.pop(rid, None)
```

> 说明：
> - `interrupt()` 的具体取值时机以 LangGraph 官方"interrupts"文档校准：interrupt 只应在节点内执行、不可在 `ainvoke` 主任务中调用；resume 通过下一次 `astream(Command(resume=approved))` 继续同一 `thread_id`。若 stream 组合模式对 `__interrupt__` 的呈现与上述不符，执行时以实际返回结构调整（保证对外事件语义一致即可）。
> - 超时（现有语义：`CONFIRM_TIMEOUT_SECONDS` 120 秒视为拒绝）→ resume=False，图内以"用户拒绝"文本结束；`/confirm` 越权/过期行为与现状保持一致（由 chat.py 的 endpoint 处理，registry 换到 runner 模块，测试相应 import 变更）。
> - 该 runner 天然支持"同一 thread 多次 interrupt"（循环逐次恢复）。

**Step 4：跑测试确认通过** → PASS。

**Step 5：提交**

```bash
git add agent/
git commit -m "feat(agent): add graph runner with interrupt/resume"
```

---

## 任务 9：router + Planner + 并行 Worker（Send 扇出）+ Collector + Summarizer

**文件：**
- 修改：`agent/app/core/graph.py`（挂新节点与条件边）
- 创建：`agent/app/core/nodes/plan.py`
- 创建：`agent/tests/test_plan_workers.py`

**Step 1：先写失败测试（图级）**

```python
@pytest.mark.asyncio
async def test_complex_request_runs_plan_workers_and_summarize():
    # 脚本：
    #   router 结构化输出 → {"mode": "plan"}
    #   planner 结构化输出 → 两个独立子任务
    #   worker（各自 mini 决策：直接给结果，不再调工具）
    #   断言：两个子任务都进入 results，final_answer 由 summarizer 产出
```

再补：`Send` 扇出的并行性（用一个可统计并发上限的 fake 工具/慢速 sleep 验证两个 worker 重叠执行）。

**Step 2：跑测试确认失败** → `ModuleNotFoundError`。

**Step 3：实现**

`nodes/plan.py` 设计：

```python
"""Plan-and-Execute 路径节点。"""
from langgraph.types import Send


class RouterDecision(BaseModel):     # with_structured_output 用
    mode: Literal["react", "plan"]
    reason: str


async def router(state, deps) -> dict:
    """判定复杂度：mode=plan 交给 planner；否则 react。结构化输出失败默认 react。"""
    # deps.router_model = model.with_structured_output(RouterDecision)
    decision = await deps.router_model.ainvoke(state["messages"])
    return {"mode": decision.mode}


class PlanStep(BaseModel):           # planner 结构化输出，字段与提示词对齐
    task_id: str
    description: str
    worker_group: Literal["search", "file_ops", "share", "recycle"]
    # 每个步骤内容自包含、无跨步依赖


class Plan(BaseModel):
    steps: list[PlanStep]


async def planner(state, deps) -> dict:
    plan: Plan = await deps.planner_model.ainvoke(state["messages"])
    return {"steps": [s.model_dump() for s in plan.steps]}


def dispatch(state) -> list[Send]:
    """子任务 → Send 扇出（LangGraph 并行 fan-out）。"""
    return [Send("worker", {"step": step, "results": []}) for step in state["steps"]]


async def worker(state, deps) -> dict:
    """单个子任务：用受限工具集跑一轮 mini ReAct（≤ worker_max_iterations 次）。
    敏感工具同样走 interrupt 确认。返回 results 归约项。"""
    # 在 worker 内部：绑定 TOOL_GROUP[step.worker_group] 子集到 worker_model
    # 若还缺参数则继续调用本组工具直至完成或超限；结束时 result = 工具最终文本
    return {"results": [{"step_id": step["task_id"], "status": "ok", "result": text}]}


async def collector(state, deps) -> dict:
    """全部 worker 汇合后：把各 step 结果整理成汇总上下文消息（给 summarizer）。"""
    # 构造一条内容 = 用户原目标 + 各子任务结果清单 的 HumanMessage，追加入 messages
    return {"messages": [...]}


async def summarizer(state, deps) -> dict:
    """对汇总上下文流式产出最终回答（emit delta），并把完整文本写入 final_answer。"""
    answer = ""
    async for chunk in deps.summarizer_model.astream(state["messages"]):
        if isinstance(chunk.content, str) and chunk.content:
            answer += chunk.content
            emit(type="delta", content=chunk.content)
    return {"final_answer": answer}
```

`graph.py` 条件边改为：

```python
def route_after_router(state) -> str:
    return "planner" if state.get("mode") == "plan" else "react_agent"
```

拓扑：

```text
START → router
router → react_agent          (mode=react)
router → planner              (mode=plan)
planner → dispatch(Send×N) → worker → collector → summarizer → END
react_agent ⇄ react_tools（循环/条件边，同任务 6/7）
```

**Step 4：跑测试确认通过** → PASS。

**Step 5：提交**

```bash
git add agent/
git commit -m "feat(agent): add plan-execute path with parallel workers"
```

---

## 任务 10：chat API 重写（接入 runner，保留全部端点与落库）

**文件：**
- 修改：`agent/app/api/chat.py`
- 修改：`agent/app/main.py`（如需：将确认注册表引用切到 runner）
- 更新：`agent/tests/test_chat_api.py`、`agent/tests/test_health.py`
- 删除：`agent/app/core/llm.py`、`agent/tests/test_llm.py`（依赖旧 LLMClient 的实现已迁移）

**Step 1：先写失败测试（改造后保持契约）**

把现有 `test_chat_api.py` 的 FakeMCP 保留（仍实现 `connect/list_tools/call_tool`），把"FakeLLM 脚本"替换为对 `chat_module._model` / `chat_module._graph_builder` 的脚本替身注入（runner 消费图，Fake 级别到"能产出约定事件"即可）；原有用例**逐条保留断言**：
- `test_sse_direct_answer`、`test_sse_tool_flow`（含 thinking/llm_decision/tool_start/tool_end/done 顺序、`resultPreview`）
- `test_session_messages_api`、`test_sessions_list_api`、`test_trace_api`（含 step 顺序 message_in→llm_decision→tool_call→answer）
- `test_confirm_timeout_rejected` / `test_confirm_approved_flow` / `test_confirm_wrong_user_rejected` / `test_confirm_unknown_request`（换到 runner 的确认注册表，语义同旧）

**Step 2：跑测试确认失败** → 预期大面积红（chat 模块引用旧 `Agent`/`LLMClient`）。

**Step 3：实现**

`chat.py` 改造要点：
- 模块级单例替换为：`_mcp: MCPClient | None`、`_checkpointer`、`_graph`（懒构建，测试 monkeypatch 入口不变）。
- 进入图前预置消息（等价旧 `_build_system_prompt`，用 `context.py` 纯函数 + 目录快照 `list_files`），构造 `messages=[SystemMessage(prompt), *history, HumanMessage(req.message)]`。
- 请求参数 `history` 仍按旧格式进入；`currentDirId/currentDirName` 用法不变。
- `thread_id = f"{req.userId}:{trace_id}"`（`trace_id` 每请求新生成），SSE 循环消费 `run_agent_stream(...)`：
  - `thinking/llm_decision` → 记 `trace`（同现状）
  - `tool_start/tool_end` → 记 `trace("tool_call")`、给 `tool_end` 补 `resultPreview`
  - `delta` 直接转发；`done` 补 `sessionId/traceId` 并存 assistant 消息与 `trace("answer")`
  - `error` → `trace("error", status="error")`
- `/confirm` endpoint 与消息/会话/trace 查询端点逻辑不动，仅确认注册表来源改为 `runner._pending_confirmations`（对外请求/响应结构不变）。

**Step 4：跑测试确认通过** → 全量回归（见任务 12 命令）。

**Step 5：提交**

```bash
git add agent/
git rm agent/app/core/llm.py agent/tests/test_llm.py
git commit -m "feat(agent): rewrite chat api onto langgraph runner"
```

---

## 任务 11：MySQL Checkpointer 启用与跨重启断点

**文件：**
- 修改：`agent/app/core/checkpoint.py`
- 修改：`agent/app/api/chat.py`（首启 `await checkpointer.setup()`）
- 创建：`agent/tests/test_checkpoint_mysql.py`（标注 `@pytest.mark.integration`，需本地 MySQL）

**Step 1：先写失败/占位测试**

```python
@pytest.mark.integration
@pytest.mark.asyncio
async def test_mysql_checkpointer_setup_and_roundtrip():
    cp = build_checkpointer(force_memory=False)   # 读 settings.mysql_checkpoint_url
    await cp.setup()
    cfg = {"configurable": {"thread_id": "it-1"}}
    await cp.aupdate(cfg, {...checkpoint/state 样例...}, {})
    # 重新 new 一个实例再 aget，验证跨实例可读（等价跨重启）
```

**Step 2：跑测试确认失败/跳过**

`pytest -m integration`（未打标记默认跳过；本地起 MySQL 后带标记跑通）。

**Step 3：实现**

- `build_checkpointer` 支持 `force_memory=False`（生产路径），chat 层启动时调用一次 `setup()`。
- 图编译：`build_agent_graph(..., checkpointer=build_checkpointer())`。
- 幂等：`setup()` 重复调用安全；表结构由库自动创建，不需手写 SQL。
- 文档注明：`r_pan_agent_*` 业务表不动；LangGraph 新增表为 `checkpoints/checkpoint_blobs/checkpoint_writes`（同一 MySQL 实例），需具备 `CREATE` 权限；定期清理按 thread 保留策略后续再做（不在本计划）。

**Step 4：验证**

本地启动 MySQL + 云盘后端 + MCP Server，手工冒烟：中途 interrupt 挂起后重启 agent 进程，再 resume 同一 thread 可继续（`thread_id` 基于 trace 不变）。

**Step 5：提交**

```bash
git add agent/
git commit -m "feat(agent): enable mysql checkpointer for cross-restart resume"
```

---

## 任务 12：全量回归与端到端联调

**Step 1：单元测试全量**

```bash
cd d:/Project/cloud-pan/agent
.\.venv\Scripts\python.exe -m pytest tests -q
```

预期：除 `integration` 标记外全绿；SSE 契约、会话/trace、确认语义均有回归用例覆盖。

**Step 2：静态/依赖核查**

```bash
.\.venv\Scripts\python.exe -m pip check
```

**Step 3：端到端冒烟（复用旧计划任务 12 步骤）**

```bash
# Java 云盘后端 :10000 / MCP Server :10001 照常启动后：
cd d:/Project/cloud-pan/agent
$env:AGENT_LLM_API_KEY = "<key>"
.\.venv\Scripts\python.exe -m uvicorn app.main:app --host 0.0.0.0 --port 8000
```

分别验证：
1. 简单请求："看看 abc 文件夹里有什么" → 事件序列 `thinking → llm_decision → tool_start → tool_end → delta → done`（ReAct 路径）。
2. 复杂/批量请求（真实 LLM）："把根目录下所有 .pdf 按年份归档到对应文件夹" → 事件出现 `planner 产出多步`、并发 `worker` 的 `tool_start/tool_end`、最终 `done`（Plan 路径；前端可见度依赖现有工具域渲染，若前端暂不渲染新事件类型，仅保证 SSE 结构合法、不中断）。
3. 敏感操作确认：删除类请求触发 `confirmation_required`，走 `/confirm` 批准后工具才执行；拒绝/超时则以拒绝文本结束。
4. 目录快照注入、"承诺搜索 nudge"、多轮会话历史等旧行为抽查不回归。
5. MySQL checkpointer：`show tables` 看到 langgraph 表；中断任务跨进程重启可 resume。

**Step 4：前端回归**

前端零改动，打开 `/agent` 页面走一遍自然语言操作（搜索/建文件夹/分享），确认 SSE 渲染与历史/链路页正常。

**Step 5：提交（如有修正）**

```bash
git add -A
git commit -m "fix(agent): tune after langgraph migration e2e"
```

---

## 风险与决策记录

| 风险/决策 | 应对 |
|---|---|
| `langgraph-checkpoint-mysql` 为社区库（非官方） | 用 `[aiomysql]` 变体；要求 MySQL ≥ 8.0.19；表结构由 `setup()` 建；若社区库不可用，降级方案：官方 `langgraph-checkpoint-postgres`（需新增 Postgres）或暂用 MemorySaver + 说明 |
| `interrupt()` 精确语义/流事件呈现随版本变化 | 任务 8 先做 spike（最小图 + 敏感工具）验证再铺开；以官方 interrupts 文档为准 |
| DeepSeek 结构化输出（router/planner）稳定性 | 一律 try/except 降级 react 或原样单步；提示词兜底 |
| MCP Streamable HTTP 单会话并发调用 | 任务 9 联调验证；若串扰则 Worker 内懒建独立 `MCPClient` 连接池 |
| `ChatOpenAI` streaming 依赖 openai SDK，包体增大 | 仅 agent 子项目；不与 Java 侧冲突 |
| SSE 长连接 + 长时间任务 | MVP 保持单次 SSE 内驱动；真正分钟级后台任务+任务列表接口列为后续增强（checkpointer 已具备前提） |
| 检查点表膨胀 | 后续按 thread 保留/定期清理，不在本计划 |

## 后续增强（不在此计划范围）

- 后台任务独立执行 + 任务进度/取消/恢复接口（`/tasks`），SSE 与执行解耦
- 多 Agent（子任务再下钻）、工具调用级时间旅行（`checkpoint.list/alist`）
- LangGraph 原生 Checkpointer 与 `r_pan_agent_trace` 的统一查询层（当前双写并存）
- 前端渲染 plan/worker 步骤视图（复用已有事件通道扩展事件字段）

## 验收清单

- [ ] `agent` 单测全绿（SSE 契约、会话/trace、确认语义回归通过）
- [ ] `/agent` 前端零改动可用：ReAct 简单请求、Planner 批量任务、`/confirm` 批准/拒绝/超时
- [ ] MySQL checkpointer 生效：langgraph 表创建；中断任务跨进程重启可 resume
- [ ] Java 云盘后端与 Java MCP Server 零改动
- [ ] `r_pan_agent_session/message/trace` 表结构与查询接口行为不变
- [ ] 执行计划中所有任务逐步提交，每步测试先红后绿
