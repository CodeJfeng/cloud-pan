"""结构化输出解析测试：不依赖网关的 JSON 文本解析。"""
import pytest
from langchain_core.messages import AIMessage

from app.core.nodes.intent import IntentDecision
from app.core.structured import extract_json_object, invoke_structured
from tests.helpers import ScriptedModel


@pytest.mark.parametrize(
    "text,expected",
    [
        ('{"a": 1}', '{"a": 1}'),
        ('```json\n{"a": 1}\n```', '{"a": 1}'),
        ("```\n{\"a\": {\"b\": 2}}\n```", '{"a": {"b": 2}}'),
        ('好的，结果如下：{"a": 1} 以上。', '{"a": 1}'),
        ("没有 JSON", None),
        ("", None),
    ],
)
def test_extract_json_object(text, expected):
    assert extract_json_object(text) == expected


@pytest.mark.asyncio
async def test_invoke_structured_parses_plain_json():
    model = ScriptedModel(
        [AIMessage(content='{"intent": "search", "confidence": 0.8, "mode": "react"}')]
    )
    decision = await invoke_structured(model, [], IntentDecision)
    assert decision is not None
    assert decision.intent == "search"
    assert decision.confidence == 0.8


@pytest.mark.asyncio
async def test_invoke_structured_parses_fenced_json_in_prose():
    """模型爱在 JSON 外面加解释/代码块：仍应解析成功。"""
    model = ScriptedModel(
        [AIMessage(content='判断结果：\n```json\n{"intent":"chitchat","confidence":0.9}\n```')]
    )
    decision = await invoke_structured(model, [], IntentDecision)
    assert decision is not None and decision.intent == "chitchat"


@pytest.mark.asyncio
async def test_invoke_structured_returns_none_without_json():
    model = ScriptedModel([AIMessage(content="我猜你想找文件")])
    assert await invoke_structured(model, [], IntentDecision) is None


@pytest.mark.asyncio
async def test_invoke_structured_returns_none_on_schema_mismatch():
    """JSON 合法但枚举值非法：返回 None 而不抛错。"""
    model = ScriptedModel([AIMessage(content='{"intent": "nonsense", "confidence": 2}')])
    assert await invoke_structured(model, [], IntentDecision) is None


@pytest.mark.asyncio
async def test_invoke_structured_raises_on_model_error():
    """模型调用异常必须向上抛出，由调用方决定降级（不吞掉网关故障）。"""

    class Boom:
        async def ainvoke(self, messages):
            raise RuntimeError("gateway down")

    with pytest.raises(RuntimeError):
        await invoke_structured(Boom(), [], IntentDecision)


@pytest.mark.asyncio
async def test_invoke_structured_accepts_ready_instance():
    """测试替身直接返回 pydantic 实例时跳过解析。"""
    model = ScriptedModel([IntentDecision(intent="browse", confidence=0.9)])
    decision = await invoke_structured(model, [], IntentDecision)
    assert decision is not None and decision.intent == "browse"
