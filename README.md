# 云智盘 Agent 系统

**以 LLM Agent 为核心交互入口的企业级智能云盘——用一句话完成文件的查找、整理、移动与分享，全过程可见、可控、可追溯。**

云智盘是一套面向企业与团队的私有云存储与协同平台。传统云盘要求用户在层层目录里点来点去，云智盘把这套交互范式换了个方向：

- **底层是扎实的企业级云盘**：Spring Boot + Vue 3 + MyBatis-Plus + Redis + 阿里云 OSS，提供多用户注册登录、文件上传下载（拖拽 / 断点续传 / 秒传）、在线预览、分享、文件夹管理与回收站等完整能力。
- **上层是能动手的 AI Agent**：基于 LangGraph 构建，把云盘的 9 个文件操作通过 **MCP（Model Context Protocol）** 封装为标准工具，由大模型理解自然语言指令、自主规划并调用工具完成任务。

用户不必记住文件放在第几级目录，也无需手动搬运：说一句"把上周的合同都找出来，挪到『客户资料』里，再给我一个分享链接"，Agent 会拆解任务、并行执行、遇到删除 / 移动等敏感操作先停下来请求确认，最后把结果说清楚。

### 设计原则

- **对话即操作，但安全边界不让渡**：敏感操作必须人工确认，`userId` 由服务端闭包注入，大模型拿不到也无法伪造用户身份。
- **能力标准化**：Agent 不直接调 HTTP 接口，云盘能力经 MCP 暴露，新增工具无需改动 Agent 代码。
- **过程透明**：思考步骤、模型决策、工具调用与耗时以 SSE 流式呈现，并按 `trace_id` 落库、可回放。
- **失败可降级**：模型能力缺失或调用异常时自动退化到更简单的执行路径，而不是直接报错中断。

## 🌟 项目特色

- Agent 优先：自然语言驱动文件操作，意图识别 + ReAct + 并行任务编排
- 企业级底座：前后端分离架构，支持高并发访问
- 安全可靠：敏感操作人工审批、AES 加密 ID、JWT 鉴权、RBAC 权限控制
- 高性能：Redis 缓存优化，提升系统响应速度
- 云存储集成：无缝集成阿里云 OSS，支持海量文件存储
- 便捷分享：灵活的文件分享机制，支持企业内部协作

## 🏗️ 系统架构

```
┌────────────────────── 交互层 ──────────────────────┐
│   Vue 3 前端：文件管理界面  +  Agent 对话面板         │
└──────────────┬──────────────────────┬──────────────┘
               │ REST                 │ SSE 流式
               ▼                      ▼
      [云智盘后端 :10000]      [云智盘 Agent :8000] ──OpenAI 兼容协议──> [LLM（DeepSeek / 通义 / Kimi…）]
               ▲                      │
               └──────────────────────┴──Streamable HTTP（MCP）──> [MCP Server :10001]
                                                                    │
                                                          9 个云盘工具（列目录 / 搜索 / 移动 / 分享 …）
```

| 模块 | 目录 | 职责 |
|------|------|------|
| 前端 | `frontend/` | 文件管理界面 + Agent 对话（流式渲染、审批弹窗、链路回放） |
| Agent 服务 | `agent/` | FastAPI + LangGraph：意图识别、任务编排、工具调用、会话持久化 |
| MCP Server | `mcp-server/` | 把云盘文件操作封装为标准 MCP 工具 |
| 云盘后端 | `server/` + `framework/` | 用户、文件、分享、权限等业务逻辑与数据 |

## 🛠️ 技术栈
### Agent 与 AI
- LangGraph - Agent 状态图编排（意图识别 / ReAct / Plan-and-Execute）
- LangChain - 模型与工具抽象层
- FastAPI + SSE - Agent 服务与流式对话接口
- MCP（Model Context Protocol）- 云盘文件能力的标准化工具协议
- MySQL Checkpointer - 图状态与多轮会话持久化
### 后端技术
- Spring Boot 2.7+ - 后端核心框架
- MyBatis-Plus 3.5+ - 数据持久层框架
- Redis 5.0+ - 缓存与会话管理
- MySQL 8.0 - 主数据库
- JWT - 身份认证机制
- 阿里云OSS - 对象存储服务
- Maven - 项目构建管理
### 前端技术
- Vue 3 - 前端核心框架
- Element Plus - UI组件库
-Axios - HTTP请求库
- Vue Router - 路由管理
- Vuex/Pinia - 状态管理

## 云盘基础能力（Agent 的能力底座）

下面这些是云盘自身的业务能力，同时也是 Agent 通过 MCP 工具可以"动手"的范围——Agent 能做的事不会超出用户的既有权限。

### 用户模块：
#### 用户功能
- 提供用户注册和登录功能，支持JWT进行认证。
- 实现了用户信息的管理与维护。
- 用户的登入、登出、忘记密码、修改密码、查询用户信息
- 封装统一的登录校验器，验证登录信息
#### 难点
- 怎样实现用户名唯一的幂等性注册接口
  - 通过数据库的username**字段唯一性**，查询数据库是否存在该username的信息，解决了重复注册的问题
- 怎样实现全局统一的登录拦截器
  - 使用**aop Aspect**切面的形式，当执行module下的方法，进入切面方法
- redis缓存登录的单机的，如何在集群模型下实现登录的信息共享
  - 该方式采用**JWT Token**的方式实现时效性以及分布式问题，通过解析token判断
  - 也可使用nginx session粘贴功能实现
#### 学习收获
- 养成良好的业务级接口的编码习惯
- 养成良好的单元测试习惯
- 学习使用切面思想实现统一的业务接口增强逻辑
- 了解幂等相关概念、提升接口的稳定性

### 文件管理：
- 支持文件的拖拽上传、断点续传和秒传功能。
- 提供文件的下载、删除、重命名和移动等基本操作。
- 支持多种文件类型的在线预览，如图片、视频、PDF和常见文档格式。
### 文件夹管理：
- 支持文件夹的创建、删除、重命名和移动。
- 实现了树状文件结构展示，方便用户浏览和管理。
### 文件分享：
- 用户可以创建私密分享链接，并设置过期时间和提取码。
- 支持将文件或文件夹分享给企业内部的其他用户或部门。
### 权限控制：
- 基于角色的访问控制（RBAC），管理员可以对不同用户的操作权限进行精细化管理。
- 支持设置文件夹的访问权限，实现不同部门或项目组之间的文件隔离。
### 回收站功能：
- 删除的文件会进入回收站，用户可以进行恢复或彻底删除。

## 🤖 云智盘 Agent（系统核心）

Agent 是云智盘的大脑：用户用自然语言对话即可操作网盘文件（查找 / 新建 / 重命名 / 删除 / 移动 / 复制 / 回收站还原 / 创建分享），全过程在前端流式可见、可审计、可干预。

### 数据流转

```
[Vue3 前端] ──SSE──> [云智盘 Agent :8000] ──OpenAI 兼容协议──> [LLM（DeepSeek / 通义 / Kimi…）]
                          │
                          └──Streamable HTTP（MCP）──> [MCP Server :10001] ──> [云盘后端 :10000]
```

### Agent 大脑：LangGraph 状态图

`agent/app/core/graph.py` 组装的完整拓扑：

```
START → intent ──chitchat→ chitchat（直答，本轮结束）→ END
              ├─react───→ react_agent ⇄ react_tools（循环）→ END
              ├─plan────→ planner → dispatch(Send×N) → worker → collector → summarizer → END
              └─clarify─→ clarify（向用户追问，本轮结束）→ END
```

- **intent（意图识别）**：由 LLM 结构化输出意图标签（`search` / `browse` / `file_ops` / `share` / `recycle` / `chitchat` / `unclear`）与置信度，取代早期的关键词规则；置信度低于 0.6 或判定为 `unclear` 一律走澄清分支，不猜。
- **chitchat**：问候、能力询问等闲聊，模型直答，不挂工具、不带目录快照，省 token。
- **react**：单步或强前后依赖的任务，走 `react_agent ⇄ react_tools` 的经典 ReAct 循环。
- **plan**：可并行的多步任务，planner 拆成自包含子任务（`PlanStep`），经 `Send` 扇出多个 worker 并行执行，collector 聚合结果后由 summarizer 流式产出最终答复。
- **clarify**：意图不明时把追问作为本轮最终回答返回，复用既有 SSE 契约，前端无需改动。

两类失败语义严格区分：模型输出解析不了 → 视为"没答清楚"，走 clarify 追问；模型调用异常（网络 / 网关故障）→ 记日志并**降级 react**，绝不伪装成"意图不明"让用户重述需求。模型不具备结构化输出或流式能力时，整图自动退化成纯 react，保证弱模型也可用。

### 工具层：MCP

Agent 不直接调云盘 HTTP 接口，而是通过 **MCP 协议**调用 `mcp-server` 暴露的工具：

| 工具 | 说明 | 工具 | 说明 |
|------|------|------|------|
| `list_files` | 列出目录内容 | `transfer_files` | 移动 |
| `search_files` | 全盘搜索 | `copy_files` | 复制 |
| `create_folder` | 新建文件夹 | `restore_file` | 回收站还原 |
| `rename_file` | 重命名 | `create_share` | 创建分享 |
| `delete_file` | 删除（进回收站） | | |

工具 schema 由 MCP 动态拉取并转换为 LangChain `StructuredTool`，新增工具只需改 MCP Server，Agent 侧无需改动。

### 安全与可靠性

- **敏感操作人工确认**：`transfer_files` / `delete_file` 执行前以 `interrupt` 挂起整图，发 `confirmation_required` 事件；用户在前端确认后 `POST /api/agent/confirm` 恢复执行，拒绝则以 `ToolMessage` 告知模型、整轮继续而非中断。
- **审批可读化**：工具工厂在 `list_files` / `search_files` 时积累 `fileId → 文件名` 索引，审批弹窗展示"移动「合同.pdf」到「工作资料」"而非一串 AES 加密 ID；无法解析的 ID 如实标为"名称待确认"，不猜也不藏。
- **身份隔离**：`userId` 由 Agent 在构造工具时闭包注入，并从 LLM 可见的 schema 中剔除；工具内部以 `Bearer MCP_<userId>` 调用云盘 API（后端 `CommonLoginAspect` 已放行该前缀），LLM 既拿不到也无法伪造用户身份。
- **根目录 ID 归一化**：后端只有 `list_files` 认 `-1` 魔法值，其余工具传 `-1` 会 AES 解密失败。Agent 侧主动把魔法值解析为真实根目录加密 ID，从根上消除这类"必失败"调用。
- **写操作回读校验**：移动 / 复制 / 新建成功后自动回读目标目录，把真实内容附加到工具返回值，模型据此基于事实作答，而不是拿到 `{"code":0}` 就宣称"已经移过去了"。
- **并行上下文隔离**：worker 的会话是节点内局部上下文，不写回全局 `messages`，避免并行分支互相污染；全局 `messages` 用 `add_messages` 归约，`results` 按 `step_id` 归约、天然幂等。

### 可观测性

- **SSE 事件**：`thinking` / `llm_decision` / `confirmation_required` / `tool_start` / `tool_end` / `delta` / `done` / `error`，前端逐步渲染思考过程、工具参数与耗时。
- **链路追踪**：每次 `/chat` 生成 `trace_id`，意图判定、模型决策、工具调用与结果、审批结论、最终答复全部落到 `agent_trace` 表，前端"查看链路"可回放一次请求的完整过程。
- **日志**：统一格式 + 轮转文件输出，`trace_id` 经 ContextVar 注入该请求的全部日志（含 Send 扇出的并行 worker）；`LLMErrorLoggingHandler` 挂在聊天模型上，任何模型调用失败都自动记录模型名、`run_id`、异常类型与完整错误体。

### 会话持久化

多轮会话与消息落 MySQL（与云智盘同库：`r_pan_agent_session` / `r_pan_agent_message`），LangGraph 状态经 MySQL checkpointer 持久化；MySQL 不可用时自动降级内存版，对话不被阻断。

### 快速体验

```powershell
# 1. 先启动云智盘后端 :10000 与 MCP Server :10001（见 mcp-server/README.md）
# 2. 启动 Agent
cd agent
python -m venv .venv
.\.venv\Scripts\python.exe -m pip install -r requirements.txt
$env:AGENT_LLM_BASE_URL = "https://api.deepseek.com/v1"   # OpenAI 兼容网关
$env:AGENT_LLM_API_KEY  = "你的Key"
$env:AGENT_LLM_MODEL    = "deepseek-chat"
.\.venv\Scripts\python.exe -m uvicorn app.main:app --host 0.0.0.0 --port 8000

# 3. 单元测试
.\.venv\Scripts\python.exe -m pytest tests -q
```

> 完整环境变量表、接口契约与日志说明见 [agent/README.md](agent/README.md)；MCP 工具清单与协议级验证见 [mcp-server/README.md](mcp-server/README.md)。

## 贡献指南
欢迎任何形式的贡献！如果你有任何建议或发现了bug，请随时提交Issue或Pull Request。

在提交代码前，请确保：
- 代码遵循项目现有的编码规范。
- 提交信息清晰明了，能够准确描述本次提交的内容。
- 提交的代码经过了充分的测试。

## 开源协议
本项目采用 [MIT License](https://opensource.org/licenses/MIT) 开源协议。