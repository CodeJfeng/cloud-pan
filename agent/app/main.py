"""FastAPI 入口：CORS + 健康检查 + 业务路由注册。"""
from fastapi import FastAPI
from fastapi.middleware.cors import CORSMiddleware

from app.api.chat import router as chat_router
from app.config import settings
from app.schemas.common import R

app = FastAPI(title="Cloud Pan Agent", version="0.1.0")

app.add_middleware(
    CORSMiddleware,
    allow_origins=["*"],
    allow_methods=["*"],
    allow_headers=["*"],
)

app.include_router(chat_router)


@app.get("/api/agent/health")
async def health() -> R:
    return R.ok(data={"status": "ok"})


def run() -> None:
    import uvicorn

    uvicorn.run("app.main:app", host="0.0.0.0", port=settings.agent_port, reload=True)
