"""DAG 版任务编排引擎（demo 实现）。

模块职责：
    schema      节点/边/执行结果的数据结构
    validate   静态体检（环检测 / 可达性 / 连接一致性）
    scheduler  邻接表、分层拓扑序、环查找
    engine     编排 + 三层容错恢复（Retry / Replan / Decompose）

当前为独立 demo，尚未接入 `app/core/graph.py` 的主状态图；
接入时只需把 Planner/Executor 换成 LLM 与 MCP Worker 实现即可。
"""
from app.planning.engine import (
    ExecContext,
    PlanEngine,
    PlanningError,
    Planner,
    RecoveryPolicy,
    RunReport,
    render_description,
)
from app.planning.schema import (
    ExecResult,
    NodeOutcome,
    PlanEdge,
    PlanGraph,
    PlanNode,
    normalize_plan,
)
from app.planning.scheduler import build_adjacency, topo_layers
from app.planning.validate import (
    InvalidPlanError,
    Issue,
    ValidationReport,
    validate_plan,
)

__all__ = [
    "ExecContext",
    "ExecResult",
    "InvalidPlanError",
    "Issue",
    "NodeOutcome",
    "PlanEdge",
    "PlanEngine",
    "PlanGraph",
    "PlanNode",
    "Planner",
    "PlanningError",
    "RecoveryPolicy",
    "RunReport",
    "ValidationReport",
    "build_adjacency",
    "normalize_plan",
    "render_description",
    "topo_layers",
    "validate_plan",
]
