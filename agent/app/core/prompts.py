"""提示词加载层：正文外置到 app/prompts/，启动导入时统一读取解析。

目录结构：
    app/prompts/
    ├── base.md                 # 唯一系统提示词：角色/风格硬约束 + 【按情形处理】规则
    ├── rules/directory.md      # 目录感知规则（注入目录快照时使用）
    ├── roles/<name>.md         # 角色提示词（intent / chitchat / planner / worker / collector）
    └── behaviors/nudge.md      # nudge 强制指令

意图判定不再按场景切提示词：全部请求统一注入 base.md，由模型自行判断适用
哪一条情形规则；`roles/intent.md` 单独供意图识别节点使用。

对外导出常量与解析后的组合文本：
    BASE_PROMPT / SYSTEM_PROMPT / DIRECTORY_RULES /
    INTENT_SYSTEM_PROMPT / CLARIFY_MESSAGE / CHITCHAT_SYSTEM_PROMPT /
    PLANNER_SYSTEM_PROMPT / WORKER_SYSTEM_PROMPT / COLLECTOR_SYSTEM_PROMPT /
    NUDGE_MESSAGE
"""
from pathlib import Path

# 提示词资源目录：app/prompts/
_PROMPTS_DIR = Path(__file__).resolve().parents[1] / "prompts"
# 角色提示词中 base 注入点标记
_BASE_MARKER = "{{BASE}}"


def _read(*parts: str) -> str:
    """读取提示词文件并去掉首尾空行，保证拼接时格式可控。"""
    path = _PROMPTS_DIR.joinpath(*parts)
    try:
        text = path.read_text(encoding="utf-8")
    except OSError as exc:
        raise RuntimeError(f"加载提示词文件失败: {path}") from exc
    return text.strip("\n")


# ---------------------------------------------------------------------------
# 系统提示词（react 与 summarizer 共用）
# ---------------------------------------------------------------------------
BASE_PROMPT = _read("base.md")
SYSTEM_PROMPT = BASE_PROMPT

# 目录感知规则：注入当前目录快照时拼接在快照之前
DIRECTORY_RULES = _read("rules", "directory.md")


def _role(name: str) -> str:
    """角色提示词：文件内 {{BASE}} 占位在加载时替换为 base 提示词正文。"""
    text = _read("roles", f"{name}.md")
    return text.replace(_BASE_MARKER, BASE_PROMPT)


# ---------------------------------------------------------------------------
# 意图识别（LLM 判定，取代原关键词路由）
# ---------------------------------------------------------------------------
# 意图识别器专用提示词：不注入 base（判定不需要执行规范）
INTENT_SYSTEM_PROMPT = _read("roles", "intent.md")
# 意图不明时的兜底追问（模型未给出 clarify 或调用失败时使用）
CLARIFY_MESSAGE = _read("behaviors", "clarify.md")
# 闲聊/能力询问直答提示词：不注入 base（base 的工具强约束会干扰闲聊）
CHITCHAT_SYSTEM_PROMPT = _read("roles", "chitchat.md")

# ---------------------------------------------------------------------------
# Plan-and-Execute 角色提示词（planner / worker / collector）
# ---------------------------------------------------------------------------
PLANNER_SYSTEM_PROMPT = _role("planner")
WORKER_SYSTEM_PROMPT = _role("worker")
COLLECTOR_SYSTEM_PROMPT = _role("collector")
# DAG 版任务规划：要求输出 nodes + edges 的结构化有向无环图（不注入 base，
# 规划阶段不需要工具执行规范，避免"按情形处理"规则干扰拆图）
PLANNER_DAG_SYSTEM_PROMPT = _read("roles", "planner_dag.md")

# 行为指令：react 节点 nudge 回环时注入
NUDGE_MESSAGE = _read("behaviors", "nudge.md")
NUDGE_LISTING_MESSAGE = _read("behaviors", "nudge_listing.md")
NUDGE_SNAPSHOT_MESSAGE = _read("behaviors", "nudge_snapshot.md")
