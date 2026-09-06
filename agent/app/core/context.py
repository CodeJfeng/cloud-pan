"""与 LLM/MCP 无关的上下文纯函数：意图路由、系统提示词解析、目录快照格式化、
承诺搜索识别、文件列表解析。

自 app/core/agent.py 抽取（原手写循环迁移到 LangGraph 节点后 agent.py 删除），
供 runner 预置系统提示词与 react/plan 节点复用。
"""
import json
import re
from typing import Any

from app.core.prompts import SCENARIO_PROMPTS, SYSTEM_PROMPT

# 意图路由关键词表：按用户消息命中场景，实现提示词按需加载。
# 顺序即优先级——"分享"是最强信号，其次是搜索，最后是文件操作。
_INTENT_KEYWORDS: dict[str, tuple[str, ...]] = {
    "share": ("分享", "共享", "提取码", "访问码"),
    "search": (
        "搜索", "查找", "查一下", "查询", "找一下", "找到", "搜一下",
        "搜到", "有没有", "在哪", "在哪里", "找出",
    ),
    "file_ops": (
        "新建", "创建", "重命名", "改名", "删除", "删掉", "移除",
        "移动", "移到", "复制", "拷贝", "还原", "恢复", "回收站",
    ),
}


def classify_intent(message: str) -> str:
    """按用户消息关键词路由到场景（share / search / file_ops / default）。"""
    for intent, keywords in _INTENT_KEYWORDS.items():
        if any(k in message for k in keywords):
            return intent
    return "default"


def resolve_system_prompt(prompt_key: str | None, user_message: str) -> str:
    """按需加载系统提示词：优先使用调用方指定的场景，否则按消息意图自动路由。"""
    key = prompt_key or classify_intent(user_message)
    return SCENARIO_PROMPTS.get(key, SYSTEM_PROMPT)


# 模型"口头承诺继续搜索/检查"却未发起工具调用的常见句式，命中后强制其继续调用工具
_PROMISED_CONTINUE_PATTERN = re.compile(
    r"(?:让我再|我再|继续搜索|继续查找|再搜索|再查一下|再找找|再试(?:一次|试)?|"
    r"再尝试|换个关键词|换个关键字|换关键词|换种方式|换个词|用其他|变体)"
)


def _looks_like_promised_search(answer: str) -> bool:
    return bool(_PROMISED_CONTINUE_PATTERN.search(answer))


def _parse_file_list(raw: str) -> list[dict[str, Any]] | None:
    """从 list_files 工具返回文本中提取文件/文件夹列表。

    工具返回格式形如 `[list_files] 云盘响应: {"code":0,...,"data":[...]}`，
    也可能直接是 JSON 文本。解析失败时返回 None（调用方静默降级）。
    """
    text = (raw or "").strip()
    # 去掉形如 "[list_files] 云盘响应: " 的前缀
    idx = text.find("{")
    if idx >= 0:
        text = text[idx:]
    try:
        payload = json.loads(text)
    except (json.JSONDecodeError, TypeError):
        return None
    data = payload.get("data") if isinstance(payload, dict) else payload
    if not isinstance(data, list):
        return None
    return [it for it in data if isinstance(it, dict)]


def _format_directory_context(
    current_dir_id: str, current_dir_name: str, items: list[dict[str, Any]] | None
) -> str:
    """把当前目录及其中条目格式化为注入提示词的文本快照。

    快照中的 fileId 供模型调用工具时使用；用中文括号包裹，提示模型这是
    内部参数。目录为空或解析失败时也给出明确说明，避免模型臆测。
    """
    name = current_dir_name or "当前文件夹"
    lines = [f"【当前目录】{name}（内部目录ID: {current_dir_id}）"]
    if not items:
        lines.append("该目录为空，或无法获取其内容。如需定位文件，请用 search_files 搜索。")
    else:
        lines.append("该目录下现有（fileId 是仅供工具调用的内部参数，不得向用户展示）:")
        for it in items:
            kind = "文件夹" if it.get("folderFlag") == 1 else "文件"
            fname = it.get("filename") or ""
            size = it.get("fileSizeDesc") or ""
            fid = it.get("fileId") or ""
            size_part = f"（{size}）" if size else ""
            lines.append(f"- {kind}「{fname}」{size_part} [fileId: {fid}]")
    return "\n".join(lines)
