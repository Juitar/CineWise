package com.miaoyu.ticket.agent.domain.confirmation;

/** C 可稳定消费的确认卡状态，不暴露历史内部中间状态。 */
public enum AgentConfirmationCardStatus {
    PENDING_CONFIRMATION,
    EXECUTING,
    RESULT_UNKNOWN,
    SUCCEEDED,
    FAILED,
    EXPIRED,
    REJECTED,
    INVALIDATED;

    /** 结果未知和终态卡片只能查看，不能再次提交确认。 */
    public boolean isReadOnly() {
        return this != PENDING_CONFIRMATION;
    }

    public static AgentConfirmationCardStatus fromActionStatus(AgentConfirmationActionStatus status) {
        return switch (status) {
            case PENDING_CONFIRMATION -> PENDING_CONFIRMATION;
            case EXECUTING -> EXECUTING;
            case RESULT_UNKNOWN -> RESULT_UNKNOWN;
            case SUCCEEDED -> SUCCEEDED;
            case FAILED -> FAILED;
            case EXPIRED -> EXPIRED;
            case REJECTED -> REJECTED;
            case INVALIDATED -> INVALIDATED;
        };
    }
}
