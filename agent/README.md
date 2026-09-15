# 云盘智能助手 Agent 服务

FastAPI 实现的云盘智能助手：用户用自然语言操作网盘文件（查找/新建/重命名/删除/移动/复制/回收站/分享）。

## 架构

```
[Vue3 前端] ──SSE──> [Agent :8000] ──OpenAI兼容协议──> [LLM(DeepSeek/通义/Kimi...)]
                          │
                          └──Streamable HTTP──> [Java MCP Server :10001] ──> [云盘后端 :10000]
```

- Agent 通过 **MCP 协议**调用 Java MCP Server（`mcp-server/`），后者把云盘 9 个文件操作能力封装成 MCP 工具
- 鉴权：工具内部以 `Bearer MCP_<userId>` 调用云盘 API（后端 `CommonLoginAspect` 已放行），`userId` 由 Agent 注入、不暴露给 LLM
- 会话持久化到 MySQL（`r_pan_agent_session` / `r_pan_agent_message`，与云盘同库）

## 快速启动

```powershell
# 1. 先启动 Java MCP Server（见 mcp-server/README.md，监听 10001）
# 2. 安装依赖并启动
cd agent
python -m venv .venv
.\.venv\Scripts\python.exe -m pip install -r requirements.txt
$env:AGENT_LLM_BASE_URL = "https://api.deepseek.com/v1"   # OpenAI 兼容网关
$env:AGENT_LLM_API_KEY  = "你的Key"
$env:AGENT_LLM_MODEL    = "deepseek-chat"
.\.venv\Scripts\python.exe -m uvicorn app.main:app --host 0.0.0.0 --port 8000
```

## 环境变量

| 变量 | 默认值 | 说明 |
|------|--------|------|
| `AGENT_PAN_BASE_URL` | `http://127.0.0.1:10000` | 云盘后端地址 |
| `AGENT_MCP_SERVER_URL` | `http://127.0.0.1:10001/mcp` | MCP Server 地址 |
| `AGENT_AGENT_PORT` | `8000` | 本服务端口 |
| `AGENT_LLM_BASE_URL` | `https://api.deepseek.com/v1` | LLM 网关（OpenAI 兼容） |
| `AGENT_LLM_API_KEY` | 空 | LLM Key |
| `AGENT_LLM_MODEL` | `deepseek-chat` | 模型名 |
| `AGENT_MYSQL_URL` | `mysql+aiomysql://root:123456@127.0.0.1:3306/cloud_drive_pan` | 会话存储 |
| `AGENT_LOG_LEVEL` | `INFO` | 日志级别 |
| `AGENT_LOG_FILE` | `logs/agent.log` | 日志文件（相对进程工作目录；目录不可写时自动降级为仅控制台） |

## 日志

统一格式，控制台 + 轮转文件（5MB × 3 份）双输出：

```
2026-09-14 18:45:14 | ERROR | app.core.llm_logging | trace=diagnose-0001 | LLM 调用失败 | model=deepseek-v4-flash | run_id=... | OpenAIInvalidRequestError: Error code: 400 - {...}
```

- **trace_id**：每次 `/chat` 请求生成（等于链路表 `agent_trace.traceId`），经 ContextVar 注入该请求的全部日志（含 Send 扇出的并行 worker），可直接用它在 `logs/agent.log` 里筛出一次请求的完整过程。
- **LLM 报错必录**：`LLMErrorLoggingHandler` 挂在聊天模型上，任何模型调用失败（网关 4xx/5xx、连接失败、超时）都会记录模型名、`run_id`、异常类型与完整错误体，无需各节点重复捕获。
- **意图识别失败**：模型输出无法解析 → warning + 走澄清；模型调用异常 → warning + 降级 react（不伪装成"意图不明"）。
- **工具/接口异常**：工具执行异常（含 worker）、`/chat` 处理异常均记录，接口异常同时写入链路表 `error` 步骤。
- `AGENT_LOG_LEVEL=DEBUG` 可看到更详细过程；`httpx`/`openai` 等第三方库已固定降到 WARNING 防刷屏。

## 接口

- `GET /api/agent/health` 健康检查
- `POST /api/agent/chat` SSE 流式对话，请求体 `{message, userId, sessionId?, currentDirId?, currentDirName?}`，事件：`thinking` / `llm_decision` / `confirmation_required` / `tool_start` / `tool_end` / `delta` / `done` / `error`
- `POST /api/agent/confirm` 敏感操作（删除/移动）批准或拒绝，请求体 `{requestId, approve, userId}`

## 测试

```powershell
.\.venv\Scripts\python.exe -m pytest tests -q
```
