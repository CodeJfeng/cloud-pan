# 提示词资源目录（app/prompts）

本项目所有 LLM 提示词正文集中维护在本目录，由 `app/core/prompts.py` 在**启动导入时一次性读取解析**并组合成常量，供各节点在运行时按角色注入。修改提示词只需改这里的 Markdown 文件，**不需要改任何 Python 代码**（新增角色除外，见下文）。

## 目录结构与文件清单

| 文件 | 内容 | 对应导出常量 | 注入位置 |
|------|------|--------------|----------|
| `base.md` | **唯一**系统提示词：角色设定、工具清单、回答风格硬约束 + 【按情形处理】A~E 规则 | `BASE_PROMPT` / `SYSTEM_PROMPT` | react 节点 `_conversation`；plan 路径 `summarizer`；角色提示词的 `{{BASE}}` 注入点 |
| `rules/directory.md` | 目录感知规则：快照/fileId 用法、未指明目录时默认根目录（-1） | `DIRECTORY_RULES` | react 节点 `_conversation` 随目录快照一并注入 |
| `roles/intent.md` | 意图识别器：意图标签、置信度档位、react/plan 模式选择、追问话术要求 | `INTENT_SYSTEM_PROMPT` | 意图节点（`nodes/intent.py`） |
| `roles/chitchat.md` | 直答角色：问候/闲聊/能力询问的口径与语气，禁止调用工具 | `CHITCHAT_SYSTEM_PROMPT` | 直答节点（`nodes/chitchat.py`） |
| `roles/planner.md` | 任务规划器角色：把复杂请求拆为相互独立的子任务 | `PLANNER_SYSTEM_PROMPT` | plan 路径 `planner` 节点 |
| `roles/worker.md` | 子任务执行者角色：受限工具集、失败如实上报 | `WORKER_SYSTEM_PROMPT` | plan 路径 `worker` 节点（代码层再追加子任务类型行） |
| `roles/collector.md` | 结果汇总者角色：汇总子任务结果、如实报告失败 | `COLLECTOR_SYSTEM_PROMPT` | 预留（当前 collector 节点只组装上下文消息） |
| `behaviors/nudge.md` | nudge 强制指令：模型承诺"我来查看/再搜一下"却不调工具时回环注入 | `NUDGE_MESSAGE` | react 节点 nudge 回环（`nodes/react.py`） |
| `behaviors/nudge_listing.md` | 无据目录宣称 nudge：无快照且无工具结果却声称"已获取到目录内容"时回环注入 | `NUDGE_LISTING_MESSAGE` | react 节点 nudge 回环（`nodes/react.py`，防虚假工具调用） |
| `behaviors/nudge_snapshot.md` | 快照指引 nudge：已注入快照时模型仍承诺"我去查看"，引导直接基于快照回答 | `NUDGE_SNAPSHOT_MESSAGE` | react 节点 nudge 回环（`nodes/react.py`） |
| `behaviors/clarify.md` | 意图不明时的兜底追问（模型未给追问或意图调用失败时使用） | `CLARIFY_MESSAGE` | 意图节点澄清分支（`nodes/intent.py`） |

## 加载与解析机制

- **启动加载**：`app/core/prompts.py` 在模块导入时读取本目录全部文件（`_read()`），缺失文件直接抛 `RuntimeError`——启动即失败，不会带病运行。
- **`{{BASE}}` 占位符**：`roles/planner.md`、`roles/worker.md`、`roles/collector.md` 以 `{{BASE}}` 标记 base 注入点，加载时整体替换为 `base.md` 正文。base 是**单一来源**——改一处，全部角色同时生效。`roles/intent.md` 不注入 base（判定阶段不需要执行规范）。
- **不再按场景切提示词**：历史上曾按关键词命中 `scenarios/*.md` 分流（search/browse/file_ops/share/default），已全部合并进 `base.md` 的【按情形处理】，由模型自行判断适用哪一段。

## 意图判定（`nodes/intent.py`）

每次对话先由 LLM 判定意图，输出 `intent` / `confidence` / `mode` / `clarify`：

```
START → intent ──chitchat→ chitchat（直答，无工具无快照）→ END
                 ├─react───→ react_agent ⇄ react_tools → END
                 ├─plan────→ planner → worker×N → collector → summarizer → END
                 └─clarify（confidence < 0.6 或判定失败）→ 向用户追问 → END
```

- 置信度阈值 `CONFIDENCE_THRESHOLD = 0.6`（`nodes/intent.py`）；低于阈值或模型输出无法解析时，把追问作为本轮回答返回。
- 追问优先用模型给出的 `clarify`，缺失时用 `behaviors/clarify.md`。
- **模型调用异常**（网络/网关故障）不伪装成"意图不明"，而是记 warning 日志并降级为 react——避免基础设施故障让用户重新描述需求。
- 结构化输出不使用 langchain 的 `with_structured_output`：DeepSeek 的 `json_mode` 要求 prompt 里出现 "json" 字样，思考模式模型又禁止指定 `tool_choice`，两条路都会 400。改为提示词约定 JSON + `app/core/structured.py` 文本解析，因此 `roles/intent.md`、`roles/planner.md` 末尾**必须保留输出格式约定**。
- `intent=chitchat` 走直答：不注入 `base.md` 的工具强约束、不注入目录快照，只带轻量直答提示词，杜绝"闲聊也去扫一遍网盘"。
- 模型不支持结构化输出时整图退化为纯 react（测试替身即此路径）；不支持流式输出时 chitchat 降级为 react。

## 变更指南

### 修改现有提示词
1. 直接编辑对应 `.md` 文件；文件首尾空行会被加载器自动去掉，不必关心。
2. 保持既有硬约束不弱化（尤其 base.md 第 5 条"如实转述工具结果、不编造"与第 9 条"要么调用工具、要么给出基于工具结果的结论"——这两条是防虚假工具调用的第一道防线）。
3. 情形规则写在 base.md 的【按情形处理】下，用 `A. B. C.` 分节，新增情形追加字母并同步更新 `tests/test_context.py` 的分节断言。
4. 运行测试确认兼容：
   ```powershell
   cd agent
   .\.venv\Scripts\python.exe -m pytest tests -q
   ```
   注意 `tests/test_context.py` 断言了角色提示词关键字（planner 含"子任务""独立"，worker 含"如实"，collector 含"遗漏"），删掉这些词会导致测试失败。

### 新增角色（plan 路径）
1. 新建 `roles/<name>.md`，需要 base 时在文件开头写 `{{BASE}}`；
2. 在 `app/core/prompts.py` 中新增 `_role("<name>")` 导出，并在对应节点注入。

### 新增意图标签
1. 在 `roles/intent.md` 的标签列表与评分规则中补充说明；
2. 在 `app/core/nodes/intent.py` 的 `IntentDecision.intent` Literal 与 `_INTENTS` 中同步添加。

## 编写规范

- 全部使用中文，条款用 `1. 2. 3.` 数字列表，与现有文件风格一致；
- 规则写成"必须/不要"式硬约束，避免模糊表述（"尽量""最好"）；
- 不要在正文里提及 `userId`、加密 ID 等内部实现细节（base.md 第 8 条）；
- 涉及工具结果的描述一律指向"工具返回"，不允许出现"我可以直接看到云端"之类绕过工具的表述。
