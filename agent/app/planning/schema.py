"""DAG 计划的数据结构：nodes 与 edges 都是一等公民。

为什么边要独立存在，而不是只在 node 上挂 `depends_on`：
1. 依赖本质是"关系"，放在边上才能携带属性（condition / 后续的数据流标签），
   将来支持条件分支时不需要改节点结构；
2. 便于校验器独立遍历图，不必先各处拼邻接表；
3. LLM 输出时"先列节点、再列边"，比在节点内部穿插 id 引用更不容易出错。

`depends_on` 仍然保留，作为节点上的便捷写法：`normalize_plan()` 会把它
展开成边，与显式 `edges` 合并去重，得到唯一权威边集（Adapter 层做兼容，
校验与执行只认权威边集，避免出现两个真相来源）。
"""
from __future__ import annotations

from typing import Any, Literal

from pydantic import BaseModel, Field

# Worker 垂直分工（与 app/core/pan_tools.py 的 TOOL_GROUP 对齐）
WorkerGroup = Literal["search", "file_ops", "share", "recycle"]
WORKER_GROUPS: tuple[str, ...] = ("search", "file_ops", "share", "recycle")

# 汇合语义：AND join（全部前驱成功）/ OR join（任一前驱成功）
JoinPolicy = Literal["all", "any"]
# 边条件：目前只支持"前驱成功后"（OR join 语义下由 join_policy 决定放行）
EdgeCondition = Literal["on_success"]

# 节点最终状态：failed 表示用尽恢复策略；skipped 表示被上游失败跳过
NodeStatus = Literal["pending", "running", "ok", "failed", "skipped"]


class PlanNode(BaseModel):
    """单个子任务。"""

    id: str = Field(description="唯一标识，建议 s1/s2/...，供边与结果引用")
    description: str = Field(description="自包含的执行说明，可用 {{s1.file_ids}} 引用上游产出")
    worker_group: WorkerGroup = "search"
    join_policy: JoinPolicy = "all"
    depends_on: list[str] = Field(default_factory=list, description="便捷写法，等价于入边")


class PlanEdge(BaseModel):
    """依赖边：src 完成后 dst 才可能被调度。"""

    src: str
    dst: str
    condition: EdgeCondition = "on_success"


class PlanGraph(BaseModel):
    """一次规划的完整产物：目标 + 节点 + 边。"""

    goal: str = ""
    nodes: list[PlanNode] = Field(default_factory=list)
    edges: list[PlanEdge] = Field(default_factory=list)


class ExecResult(BaseModel):
    """Worker 执行单个节点的结果。

    `transient=True` 表示瞬时故障（超时/限流），值得重试；
    False 表示确定性失败（参数非法/权限不足），重试无意义，应直接换方法。
    """

    ok: bool
    output: str = ""
    data: dict[str, Any] = Field(default_factory=dict)
    error: str | None = None
    transient: bool = False


class NodeOutcome(BaseModel):
    """节点在本次运行中的最终归宿（含走过的恢复阶段）。

    `children` 只在 Layer3 再分解后出现：父节点吸收子图结果保持上层视图干净，
    同时保留明细以便追溯"这个节点是靠哪几个细粒度步骤完成的"。
    """

    node_id: str
    status: NodeStatus = "pending"
    output: str = ""
    data: dict[str, Any] = Field(default_factory=dict)
    error: str | None = None
    attempts: int = 0
    stage: str = "none"  # none | retry | replan | decompose | exhausted
    note: str = ""
    children: dict[str, "NodeOutcome"] = Field(default_factory=dict)


NodeOutcome.model_rebuild()


def join_field_declared(node: PlanNode) -> bool:
    """join_policy 是否在原始输出中显式声明（而非 pydantic 填的默认值）。

    供"连接一致性"检查使用：多前驱汇合却没写 join_policy 属于表述不完整，
    虽然可以取默认 AND，但应当在体检报告里点出来。
    """
    return "join_policy" in getattr(node, "model_fields_set", set())


def normalize_plan(plan: PlanGraph) -> PlanGraph:
    """把 `depends_on` 展开为边并与显式边合并去重，返回只有权威边集的副本。"""
    seen: set[tuple[str, str]] = set()
    edges: list[PlanEdge] = []
    for node in plan.nodes:
        for dep in node.depends_on:
            key = (dep, node.id)
            if key in seen:
                continue
            seen.add(key)
            edges.append(PlanEdge(src=dep, dst=node.id))
    for edge in plan.edges:
        key = (edge.src, edge.dst)
        if key in seen:
            continue
        seen.add(key)
        edges.append(edge.model_copy(deep=True))
    new_nodes = [n.model_copy(update={"depends_on": []}) for n in plan.nodes]
    return PlanGraph(goal=plan.goal, nodes=new_nodes, edges=edges)
