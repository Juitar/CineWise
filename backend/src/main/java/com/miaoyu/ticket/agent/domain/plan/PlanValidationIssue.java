package com.miaoyu.ticket.agent.domain.plan;

/** 一个可定位到节点和字段路径的计划校验问题。 */
public record PlanValidationIssue(
        PlanValidationIssueCode code,
        String nodeId,
        String fieldPath,
        String message) {
}
