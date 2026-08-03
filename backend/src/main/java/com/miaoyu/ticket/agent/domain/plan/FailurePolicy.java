package com.miaoyu.ticket.agent.domain.plan;

/** 节点失败后的处理策略。 */
public enum FailurePolicy {
    RETRY_ONCE,
    REPLAN,
    ASK_USER,
    FAIL
}
