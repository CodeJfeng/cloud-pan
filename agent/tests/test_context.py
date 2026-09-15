import pytest

from app.core.prompts import (
    BASE_PROMPT,
    CLARIFY_MESSAGE,
    COLLECTOR_SYSTEM_PROMPT,
    INTENT_SYSTEM_PROMPT,
    PLANNER_SYSTEM_PROMPT,
    SYSTEM_PROMPT,
    WORKER_SYSTEM_PROMPT,
)


def test_single_system_prompt_covers_all_situations():
    """提示词只有一份：base 含全部情形规则，不再按场景切分。"""
    assert SYSTEM_PROMPT == BASE_PROMPT
    for marker in ("A.", "B.", "C.", "D.", "E."):
        assert marker in SYSTEM_PROMPT
    # 关键情形规则仍在：浏览基于快照、搜索至少两轮、操作先确认、分享给链接
    assert "基于快照回答" in SYSTEM_PROMPT
    assert "至少尝试 2 次不同关键词" in SYSTEM_PROMPT
    assert "请求用户确认后再执行" in SYSTEM_PROMPT
    assert "提取码" in SYSTEM_PROMPT
    assert "不要编造工具没有返回的信息" in SYSTEM_PROMPT


def test_intent_prompt_defines_labels_and_scoring():
    """意图识别提示词：定义标签、置信度档位与模式选择。"""
    for label in ("search", "browse", "file_ops", "share", "recycle", "chitchat", "unclear"):
        assert label in INTENT_SYSTEM_PROMPT
    assert "confidence" in INTENT_SYSTEM_PROMPT
    assert "0.6" in INTENT_SYSTEM_PROMPT
    assert "react" in INTENT_SYSTEM_PROMPT and "plan" in INTENT_SYSTEM_PROMPT
    # 要求模型直接输出 JSON（网关无关的解析依赖此约定）
    assert "JSON" in INTENT_SYSTEM_PROMPT
    # 闲聊/问候不得被判为低置信度追问
    assert "chitchat" in INTENT_SYSTEM_PROMPT
    assert CLARIFY_MESSAGE.strip()


@pytest.mark.parametrize(
    "raw,expected",
    [
        ('[list_files] 云盘响应: {"code":0,"data":[{"fileId":"f1","filename":"a.txt"}]}', 1),
        ('{"data":[{"fileId":"f1"},{"fileId":"f2"}]}', 2),
        ("non-json", 0),
    ],
)
def test_parse_file_list(raw, expected):
    from app.core.context import _parse_file_list

    items = _parse_file_list(raw)
    assert (len(items) if items is not None else 0) == expected


def test_planner_worker_collector_prompts_present():
    assert "子任务" in PLANNER_SYSTEM_PROMPT
    assert "独立" in PLANNER_SYSTEM_PROMPT
    assert WORKER_SYSTEM_PROMPT and COLLECTOR_SYSTEM_PROMPT
    # Worker 与 Collector 面向执行/汇总角色，含结果如实上报约束
    assert "如实" in WORKER_SYSTEM_PROMPT
    assert "遗漏" in COLLECTOR_SYSTEM_PROMPT
