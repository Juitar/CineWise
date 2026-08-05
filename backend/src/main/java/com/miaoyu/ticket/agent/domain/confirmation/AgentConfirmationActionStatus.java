package com.miaoyu.ticket.agent.domain.confirmation;

/** 一次性确认动作的服务端状态；终态和结果未知均不得重新进入写工具。 */
public enum AgentConfirmationActionStatus {
    PENDING_CONFIRMATION,
    EXECUTING,
    RESULT_UNKNOWN,
    SUCCEEDED,
    FAILED,
    REJECTED,
    EXPIRED,
    INVALIDATED;

    /** 用户不再能对终态动作再次确认。 */
    public boolean isTerminal() {
        return this == SUCCEEDED || this == FAILED || this == REJECTED || this == EXPIRED || this == INVALIDATED;
    }

    /** 该状态表示已有写请求，必须只查询原结果而不是重发。 */
    public boolean requiresResultRecovery() {
        return this == RESULT_UNKNOWN;
    }
}
