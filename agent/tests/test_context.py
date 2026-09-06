import pytest

from app.core.context import classify_intent
from app.core.prompts import (
    COLLECTOR_SYSTEM_PROMPT,
    PLANNER_SYSTEM_PROMPT,
    WORKER_SYSTEM_PROMPT,
)


@pytest.mark.parametrize(
    "message,expected",
    [
        ("帮我搜索李京峰简历", "search"),
        ("查找“李京峰-个人简历”的文件", "search"),
        ("有没有叫合同的文件", "search"),
        ("把这个文件夹分享给同事", "share"),
        ("创建分享链接", "share"),
        ("新建一个文件夹", "file_ops"),
        ("删除这个文件", "file_ops"),
        ("把文件移动到项目目录", "file_ops"),
        ("你好", "default"),
        ("你好，今天天气不错", "default"),
    ],
)
def test_classify_intent(message, expected):
    assert classify_intent(message) == expected


def test_planner_worker_collector_prompts_present():
    assert "子任务" in PLANNER_SYSTEM_PROMPT
    assert "独立" in PLANNER_SYSTEM_PROMPT
    assert WORKER_SYSTEM_PROMPT and COLLECTOR_SYSTEM_PROMPT
    # Worker 与 Collector 面向执行/汇总角色，含结果如实上报约束
    assert "如实" in WORKER_SYSTEM_PROMPT
    assert "遗漏" in COLLECTOR_SYSTEM_PROMPT
