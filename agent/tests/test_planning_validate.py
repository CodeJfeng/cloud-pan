"""计划静态体检的单元测试：环 / 可达性 / 连接一致性 / 结构合法性。"""
from app.planning import PlanEdge, PlanGraph, PlanNode, validate_plan


def _code_set(report) -> set[str]:
    return {i.code for i in report.issues}


def test_valid_dag_passes():
    plan = PlanGraph(
        goal="备份照片并分享",
        nodes=[
            PlanNode(id="s1", description="搜索 jpg", worker_group="search"),
            PlanNode(id="s2", description="创建备份目录", worker_group="file_ops"),
            PlanNode(
                id="s3",
                description="移动文件",
                worker_group="file_ops",
                join_policy="all",
            ),
        ],
        edges=[PlanEdge(src="s1", dst="s3"), PlanEdge(src="s2", dst="s3")],
    )
    report = validate_plan(plan)
    assert report.ok, report.render_feedback()
    assert not report.warnings


def test_cycle_is_rejected_with_path():
    plan = PlanGraph(
        nodes=[PlanNode(id=f"s{i}", description="x") for i in (1, 2, 3)],
        edges=[
            PlanEdge(src="s1", dst="s2"),
            PlanEdge(src="s2", dst="s3"),
            PlanEdge(src="s3", dst="s2"),  # 成环
        ],
    )
    report = validate_plan(plan)
    assert not report.ok
    assert "E_CYCLE" in _code_set(report)
    cycle_issue = next(i for i in report.issues if i.code == "E_CYCLE")
    assert "s2" in cycle_issue.nodes and "s3" in cycle_issue.nodes
    # 体检结论要能直接告诉 Planner 该怎么改
    assert "打破环" in cycle_issue.hint


def test_ring_disconnected_from_root_is_unreachable():
    """独立的环与起点没有通路 → 环 + 孤岛两个错误都要报出来。"""
    plan = PlanGraph(
        nodes=[PlanNode(id=f"{c}", description="x") for c in ("r", "a", "b")],
        edges=[
            PlanEdge(src="a", dst="b"),
            PlanEdge(src="b", dst="a"),
        ],
    )
    report = validate_plan(plan)
    assert not report.ok
    assert {"E_CYCLE", "E_UNREACHABLE"} <= _code_set(report)


def test_no_root_when_every_node_has_predecessor():
    """所有节点都在环上 → 没有起点，计划永远无法开始。"""
    plan = PlanGraph(
        nodes=[PlanNode(id="a", description="x"), PlanNode(id="b", description="y")],
        edges=[PlanEdge(src="a", dst="b"), PlanEdge(src="b", dst="a")],
    )
    report = validate_plan(plan)
    assert not report.ok
    assert {"E_CYCLE", "E_NO_ROOT"} <= _code_set(report)


def test_self_loop_is_rejected():
    plan = PlanGraph(
        nodes=[PlanNode(id="s1", description="x")],
        edges=[PlanEdge(src="s1", dst="s1")],
    )
    report = validate_plan(plan)
    assert not report.ok
    assert "E_SELF_LOOP" in _code_set(report)


def test_dangling_edge_reference():
    plan = PlanGraph(
        nodes=[PlanNode(id="s1", description="x")],
        edges=[PlanEdge(src="s1", dst="s99")],
    )
    report = validate_plan(plan)
    assert not report.ok
    assert "E_EDGE_UNKNOWN" in _code_set(report)


def test_multiple_roots_are_parallel_branches():
    """多个起始节点是合法的：它们是天然并行的分支，不是孤岛。

    真正的"孤岛"是**有入边却无法从任何起点正向到达**的节点，那只可能由环
    造成（见 test_cycle_is_rejected_with_path），所以这里期望体检放行。
    """
    plan = PlanGraph(
        nodes=[PlanNode(id="s1", description="x"), PlanNode(id="s2", description="并行分支")],
        edges=[],
    )
    report = validate_plan(plan)
    assert report.ok, report.render_feedback()


def test_chain_dependency_is_reachable():
    plan = PlanGraph(
        nodes=[PlanNode(id="s1", description="x"), PlanNode(id="s2", description="y")],
        edges=[PlanEdge(src="s1", dst="s2")],
    )
    report = validate_plan(plan)
    assert report.ok
    assert not report.issues


def test_duplicate_node_id():
    plan = PlanGraph(
        nodes=[PlanNode(id="s1", description="a"), PlanNode(id="s1", description="b")],
        edges=[],
    )
    report = validate_plan(plan)
    assert not report.ok
    assert "E_ID_DUP" in _code_set(report)


def test_empty_plan():
    report = validate_plan(PlanGraph())
    assert not report.ok
    assert "E_EMPTY" in _code_set(report)


def test_join_policy_undeclared_warns():
    """多前驱汇合却没声明 AND/OR → warning（可默认 AND 继续跑）。"""
    raw = '{"goal":"g","nodes":[{"id":"s3","description":"汇总"}],"edges":[]}'
    _ = raw
    plan = PlanGraph.model_validate_json(
        '{"goal":"g","nodes":[{"id":"s1","description":"a"},'
        '{"id":"s2","description":"b"},'
        '{"id":"s3","description":"c"}],'
        '"edges":[{"src":"s1","dst":"s3"},{"src":"s2","dst":"s3"}]}'
    )
    report = validate_plan(plan)
    assert report.ok
    assert "W_JOIN_UNDECLARED" in _code_set(report)


def test_or_join_on_root_warns():
    plan = PlanGraph(
        nodes=[PlanNode(id="s1", description="x", join_policy="any")],
        edges=[],
    )
    report = validate_plan(plan)
    assert report.ok
    assert "W_JOIN_ANY_ROOT" in _code_set(report)


def test_render_feedback_contains_codes_and_hints():
    plan = PlanGraph(
        nodes=[PlanNode(id="s1", description="x")],
        edges=[PlanEdge(src="s1", dst="s1")],
    )
    feedback = validate_plan(plan).render_feedback()
    assert "E_SELF_LOOP" in feedback
    assert "修正建议" in feedback
