"""计划静态体检：进入执行引擎前的强制校验（DAG 守门人）。

设计取舍
--------
LLM 只负责"提议"图结构，**是否合法一律由这里的确定性代码判定**。
原因很直接：prompt 约束不具备幂等性——同一个请求换个温度或重试一次，就可能
产出非法图。只有把规则固化成校验器，才能保证执行引擎拿到的永远是 DAG。

结果分两级：
- error   ：结构性错误，图不可用，必须退回 Planner 重新规划（或降级串行执行）
- warning ：表述不完整（如 AND/OR join 未显式声明），取默认策略后可继续执行

体检报告的 `render_feedback()` 会生成给 Planner 看的自然语言反馈，
让"重规划"说的是具体问题而不是笼统的"再试一次"。
"""
from __future__ import annotations

from dataclasses import dataclass, field

from app.planning.scheduler import Adjacency, build_adjacency, find_cycle, find_roots
from app.planning.schema import PlanGraph, PlanNode, join_field_declared

LEVEL_ERROR = "error"
LEVEL_WARNING = "warning"


class InvalidPlanError(Exception):
    """计划未通过静态体检，不应进入执行引擎。"""

    def __init__(self, report: "ValidationReport") -> None:
        super().__init__(report.render_feedback())
        self.report = report


@dataclass(frozen=True)
class Issue:
    """一条体检结论。"""

    code: str
    level: str
    message: str
    nodes: tuple[str, ...] = ()
    hint: str = ""


@dataclass
class ValidationReport:
    issues: list[Issue] = field(default_factory=list)

    @property
    def errors(self) -> list[Issue]:
        return [i for i in self.issues if i.level == LEVEL_ERROR]

    @property
    def warnings(self) -> list[Issue]:
        return [i for i in self.issues if i.level == LEVEL_WARNING]

    @property
    def ok(self) -> bool:
        return not self.errors

    def raise_for_status(self) -> "ValidationReport":
        if not self.ok:
            raise InvalidPlanError(self)
        return self

    def render_feedback(self, *, include_warnings: bool = True) -> str:
        """把结论渲染成给 Planner 的重规划反馈文本。"""
        picked = list(self.errors)
        if include_warnings:
            picked += self.warnings
        if not picked:
            return "静态体检通过。"
        lines = ["上一版计划未通过静态体检，请修正后重新输出完整 JSON："]
        for issue in picked:
            suffix = f"（节点：{'、'.join(issue.nodes)}）" if issue.nodes else ""
            lines.append(f"- [{issue.level}] {issue.code}：{issue.message}{suffix}")
            if issue.hint:
                lines.append(f"  修正建议：{issue.hint}")
        return "\n".join(lines)


# ---------------------------------------------------------------------------
# 各项检查
# ---------------------------------------------------------------------------


def _check_nodes(report: ValidationReport, plan: PlanGraph) -> None:
    if not plan.nodes:
        report.issues.append(
            Issue("E_EMPTY", LEVEL_ERROR, "计划为空：没有任何子任务", hint="至少拆出一个子任务")
        )
        return
    seen: set[str] = set()
    for node in plan.nodes:
        nid = node.id.strip()
        if not nid:
            report.issues.append(
                Issue("E_ID_BLANK", LEVEL_ERROR, "存在空 id 的子任务", hint="每个节点都要有唯一 id")
            )
            continue
        if nid in seen:
            report.issues.append(
                Issue(
                    "E_ID_DUP",
                    LEVEL_ERROR,
                    f"子任务 id 重复：{nid}",
                    nodes=(nid,),
                    hint="id 必须全局唯一，否则下游引用会有歧义",
                )
            )
        seen.add(nid)


def _check_edges(report: ValidationReport, plan: PlanGraph) -> None:
    known = {n.id for n in plan.nodes}
    for edge in plan.edges:
        if edge.src not in known or edge.dst not in known:
            missing = [x for x in (edge.src, edge.dst) if x not in known]
            report.issues.append(
                Issue(
                    "E_EDGE_UNKNOWN",
                    LEVEL_ERROR,
                    f"依赖边引用了不存在的节点：{edge.src} -> {edge.dst}",
                    nodes=tuple(missing),
                    hint="删除该边，或补上缺失的节点",
                )
            )
        if edge.src == edge.dst:
            report.issues.append(
                Issue(
                    "E_SELF_LOOP",
                    LEVEL_ERROR,
                    f"节点自环：{edge.src} 依赖自己",
                    nodes=(edge.src,),
                    hint="自依赖必须删除",
                )
            )


def _check_acyclic(report: ValidationReport, adj: Adjacency) -> None:
    """环检测：DAG 的根本要求。"""
    cycle = find_cycle(adj)
    if cycle:
        report.issues.append(
            Issue(
                "E_CYCLE",
                LEVEL_ERROR,
                f"存在循环依赖：{' -> '.join(cycle)}",
                nodes=tuple(dict.fromkeys(cycle)),
                hint="打破环：让其中一个节点不再依赖环内的后继节点",
            )
        )


def _check_reachability(report: ValidationReport, adj: Adjacency) -> None:
    """可达性：每个节点都应能从起始节点到达，不允许"孤岛"任务。"""
    if not adj.node_ids:
        return
    roots = find_roots(adj)
    if not roots:
        # 所有节点都有入度 ⇒ 必然成环，由环检测给出具体路径，这里只点出问题
        report.issues.append(
            Issue(
                "E_NO_ROOT",
                LEVEL_ERROR,
                "没有任何起始节点（所有节点都有前驱），计划无法开始执行",
                hint="至少保留一个不依赖任何节点的第一步",
            )
        )
        return
    # 从全部起始节点做正向遍历
    reachable: set[str] = set()
    queue = list(roots)
    while queue:
        nid = queue.pop()
        if nid in reachable:
            continue
        reachable.add(nid)
        queue.extend(adj.succ[nid])
    orphan = [nid for nid in adj.node_ids if nid not in reachable]
    if orphan:
        report.issues.append(
            Issue(
                "E_UNREACHABLE",
                LEVEL_ERROR,
                f"孤岛任务（无法从起始节点到达）：{'、'.join(orphan)}",
                nodes=tuple(orphan),
                hint="为它们补上与主流程的依赖边，或直接删除",
            )
        )


def _check_join_consistency(report: ValidationReport, adj: Adjacency, nodes: dict[str, PlanNode]) -> None:
    """连接一致性：多前驱汇合时必须显式声明 AND/OR，且语义不能自相矛盾。"""
    for nid in adj.node_ids:
        node = nodes.get(nid)
        if node is None:
            continue
        preds = adj.pred[nid]
        policy = node.join_policy
        if len(preds) >= 2 and not join_field_declared(node):
            report.issues.append(
                Issue(
                    "W_JOIN_UNDECLARED",
                    LEVEL_WARNING,
                    f"节点 {nid} 有 {len(preds)} 个前驱但未显式声明 join_policy，已按 AND(all) 处理",
                    nodes=(nid,),
                    hint="明确写 \"join_policy\": \"all\" 或 \"any\"",
                )
            )
        if not preds and policy == "any":
            report.issues.append(
                Issue(
                    "W_JOIN_ANY_ROOT",
                    LEVEL_WARNING,
                    f"起始节点 {nid} 使用了 OR(any) 汇合策略，但没有前驱，语义无效",
                    nodes=(nid,),
                    hint="起始节点应省略 join_policy 或使用 all",
                )
            )


# ---------------------------------------------------------------------------
# 对外入口
# ---------------------------------------------------------------------------


def validate_plan(plan: PlanGraph) -> ValidationReport:
    """执行一次完整静态体检（不抛异常，返回报告供上层决定处置方式）。"""
    report = ValidationReport()
    _check_nodes(report, plan)
    if not plan.nodes:
        return report
    _check_edges(report, plan)
    adj = build_adjacency(plan)
    nodes = {n.id: n for n in plan.nodes}
    _check_acyclic(report, adj)
    _check_reachability(report, adj)
    _check_join_consistency(report, adj, nodes)
    return report


def ensure_valid_plan(plan: PlanGraph) -> PlanGraph:
    """校验通过才返回，否则抛 InvalidPlanError。"""
    validate_plan(plan).raise_for_status()
    return plan
