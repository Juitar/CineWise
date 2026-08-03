package com.miaoyu.ticket.agent.domain.plan;

/** 服务端运行计划节点的执行状态。 */
public enum PlanNodeStatus {
    PENDING,
    RUNNING,
    WAITING_CONFIRMATION,
    SUCCESS,
    FAILED,
    SKIPPED
}
