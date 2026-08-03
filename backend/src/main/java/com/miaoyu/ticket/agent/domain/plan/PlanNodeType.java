package com.miaoyu.ticket.agent.domain.plan;

/** 模型计划允许使用的固定节点类型。 */
public enum PlanNodeType {
    ASK_USER,
    CALL_TOOL,
    COMPUTE,
    VALIDATE,
    CONFIRM_ACTION,
    RENDER_RESULT
}
