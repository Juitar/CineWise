package com.miaoyu.ticket.agent.domain.plan;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

/** 计划校验的结构化结果，无效计划不会携带可执行计划。 */
public final class PlanValidationResult {
    private final List<PlanValidationIssue> issues;
    private final ExecutionPlan executionPlan;

    private PlanValidationResult(List<PlanValidationIssue> issues, ExecutionPlan executionPlan) {
        this.issues = List.copyOf(issues);
        this.executionPlan = executionPlan;
        if (this.issues.isEmpty() && executionPlan == null) {
            throw new IllegalArgumentException("有效结果必须携带运行计划");
        }
        if (!this.issues.isEmpty() && executionPlan != null) {
            throw new IllegalArgumentException("无效结果不能携带运行计划");
        }
    }

    /** 创建包含可执行运行计划的有效结果。 */
    public static PlanValidationResult valid(ExecutionPlan executionPlan) {
        return new PlanValidationResult(List.of(), Objects.requireNonNull(executionPlan, "executionPlan 不能为空"));
    }

    /** 创建只包含问题列表的无效结果。 */
    public static PlanValidationResult invalid(List<PlanValidationIssue> issues) {
        if (issues == null || issues.isEmpty()) {
            throw new IllegalArgumentException("无效结果必须至少包含一个问题");
        }
        return new PlanValidationResult(issues, null);
    }

    public boolean isValid() {
        return issues.isEmpty();
    }

    public List<PlanValidationIssue> issues() {
        return issues;
    }

    public Optional<ExecutionPlan> executionPlan() {
        return Optional.ofNullable(executionPlan);
    }
}
