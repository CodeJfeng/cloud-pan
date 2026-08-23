"""统一响应模型（与 Java 后端 R<T> 结构保持一致）。"""
from typing import Any, Generic, TypeVar

from pydantic import BaseModel

T = TypeVar("T")


class R(BaseModel, Generic[T]):
    code: int = 200
    message: str = "success"
    data: T | None = None

    @staticmethod
    def ok(data: Any = None, message: str = "success") -> "R":
        return R(code=200, message=message, data=data)

    @staticmethod
    def fail(message: str, code: int = 500) -> "R":
        return R(code=code, message=message, data=None)
