import pytest

from app.storage.session_store import SessionStore


@pytest.fixture
async def store():
    s = SessionStore("sqlite+aiosqlite:///:memory:")
    await s.init()
    yield s
    await s.close()


@pytest.mark.asyncio
async def test_create_and_list_sessions(store):
    sid = await store.create_session(user_id=1, title="测试会话")
    sessions = await store.list_sessions(user_id=1)
    assert len(sessions) == 1
    assert sessions[0]["session_id"] == sid
    assert sessions[0]["title"] == "测试会话"


@pytest.mark.asyncio
async def test_save_and_load_history(store):
    sid = await store.create_session(user_id=1, title="会话")
    await store.save_message(sid, "user", "你好")
    await store.save_message(sid, "assistant", "你好呀", tool_name="list_files")
    history = await store.load_history(sid)
    assert [h["role"] for h in history] == ["user", "assistant"]
    assert history[1]["content"] == "你好呀"
    assert history[1]["tool_name"] == "list_files"


@pytest.mark.asyncio
async def test_load_history_limit(store):
    sid = await store.create_session(user_id=1, title="会话")
    for i in range(5):
        await store.save_message(sid, "user", f"msg{i}")
    history = await store.load_history(sid, limit=3)
    assert [h["content"] for h in history] == ["msg2", "msg3", "msg4"]


@pytest.mark.asyncio
async def test_sessions_scoped_by_user(store):
    await store.create_session(user_id=1, title="A")
    await store.create_session(user_id=2, title="B")
    assert len(await store.list_sessions(user_id=1)) == 1
