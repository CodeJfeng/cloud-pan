"""图遍历工具：邻接表构建、分层拓扑序、环查找。

校验器与调度器共用同一套邻接计算，避免"体检通过的图"和"实际执行的图"
在边集理解上出现分歧。
"""
from __future__ import annotations

from dataclasses import dataclass, field

from app.planning.schema import PlanGraph, PlanNode


@dataclass(frozen=True)
class Adjacency:
    """有向图的邻接视图。"""

    succ: dict[str, list[str]] = field(default_factory=dict)
    pred: dict[str, list[str]] = field(default_factory=dict)
    indeg: dict[str, int] = field(default_factory=dict)

    @property
    def node_ids(self) -> list[str]:
        return list(self.succ)


def build_adjacency(plan: PlanGraph) -> Adjacency:
    """按 nodes 原顺序构建邻接表（自环也会被计入，交由校验器判定）。"""
    succ: dict[str, list[str]] = {n.id: [] for n in plan.nodes}
    pred: dict[str, list[str]] = {n.id: [] for n in plan.nodes}
    for edge in plan.edges:
        # 悬空边在校验阶段已报错；这里跳过以免 KeyError 掩盖真正的结构错误
        if edge.src not in succ or edge.dst not in succ:
            continue
        if edge.src == edge.dst:
            continue
        if edge.dst in succ[edge.src]:
            continue
        succ[edge.src].append(edge.dst)
        pred[edge.dst].append(edge.src)
    indeg = {nid: len(pred[nid]) for nid in succ}
    return Adjacency(succ=succ, pred=pred, indeg=indeg)


def find_roots(adj: Adjacency) -> list[str]:
    """入度为 0 的节点，按 nodes 原顺序返回。"""
    return [nid for nid in adj.node_ids if adj.indeg[nid] == 0]


def topo_layers(plan: PlanGraph) -> list[list[str]]:
    """Kahn 分层：同层节点互相无依赖，可安全并行；层与层之间是同步屏障。

    要求图已通过静态体检（无环），否则抛 ValueError——调度器不应该悄悄
    吞掉环，那是校验器的职责边界。
    """
    adj = build_adjacency(plan)
    remaining = dict(adj.indeg)
    frontier = find_roots(adj)
    layers: list[list[str]] = []
    visited = 0
    while frontier:
        layers.append(list(frontier))
        visited += len(frontier)
        nxt: list[str] = []
        for nid in frontier:
            for dst in adj.succ[nid]:
                remaining[dst] -= 1
                if remaining[dst] == 0:
                    nxt.append(dst)
        frontier = nxt
    if visited != len(adj.node_ids):
        raise ValueError("图中存在环，无法拓扑分层（应先执行静态体检）")
    return layers


def find_cycle(adj: Adjacency) -> list[str] | None:
    """DFS 三色标记找环，返回环上的节点序列（如 ['s2','s3','s2']），无环返回 None。"""
    WHITE, GRAY, BLACK = 0, 1, 2
    color = {nid: WHITE for nid in adj.node_ids}
    stack: list[str] = []

    def dfs(nid: str) -> list[str] | None:
        color[nid] = GRAY
        stack.append(nid)
        for nxt in adj.succ[nid]:
            if color[nxt] == GRAY:  # 回边
                idx = stack.index(nxt)
                return stack[idx:] + [nxt]
            if color[nxt] == WHITE:
                found = dfs(nxt)
                if found:
                    return found
        stack.pop()
        color[nid] = BLACK
        return None

    for nid in adj.node_ids:
        if color[nid] == WHITE:
            found = dfs(nid)
            if found:
                return found
    return None


def index_nodes(plan: PlanGraph) -> dict[str, PlanNode]:
    """id -> 节点。重复 id 时后者覆盖，是否合法由校验器判定。"""
    return {n.id: n for n in plan.nodes}
