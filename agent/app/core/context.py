"""与 LLM/MCP 无关的上下文纯函数：承诺搜索识别、文件列表解析、目录快照格式化。

自 app/core/agent.py 抽取（原手写循环迁移到 LangGraph 节点后 agent.py 删除），
供 runner 预置目录快照与 react/plan 节点复用。

意图判定已改由 LLM 完成（app/core/nodes/intent.py），不再在此做关键词路由。
"""
import json
import re
from typing import Any


# 模型"口头承诺要查看/搜索/检查"却未发起工具调用的句式（承诺态，区别于完成态宣称）：
# a) 再/继续/换关键词类（继续尝试）；b) 我来/我去/这就来/现在去 + 动词（将来时标记，
#    用单字 lookahead 避免误伤"我来自/我去年"等）；c) 让我 + 动词（意图语气）
_PROMISED_ACTION_PATTERN = re.compile(
    r"(?:让我再|我再|继续搜索|继续查找|再搜索|再查一下|再找找|再试(?:一次|试)?|"
    r"再尝试|换个关键词|换个关键字|换关键词|换种方式|换个词|用其他|变体|"
    r"(?:让我|我)(?:这就|现在就?|马上|先)?(?:来|去)"
    r"(?=看|查|找|搜|获|列|检|确|核|处|操|执|删|建|创|移|复|分)|"
    r"让我(?:看看|查看|查查|查一下|找找|搜一下|检查一下|确认一下)"
    r")"
)


def _looks_like_promised_action(answer: str) -> bool:
    return bool(_PROMISED_ACTION_PATTERN.search(answer))


# 无据目录宣称：模型在"无快照且无工具结果"的情况下声称已获取/列出了目录内容
# （虚假工具调用的典型表现，如"我获取到了…共有 3 个文件夹"）
_UNBACKED_LISTING_PATTERN = re.compile(
    r"(?:获取到|拿到|列出|查看|看到|扫描)[了过]"
    r".{0,16}?"
    r"(?:目录|文件夹|文件列表|内容)"
    r"|共\s*有\s*\d+\s*个\s*(?:文件夹|文件|目录)"
)


def _looks_like_unbacked_listing(answer: str) -> bool:
    """回答是否宣称"已经获取/列出目录内容"（完成态叙述，区别于能力描述）。"""
    return bool(_UNBACKED_LISTING_PATTERN.search(answer))


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
