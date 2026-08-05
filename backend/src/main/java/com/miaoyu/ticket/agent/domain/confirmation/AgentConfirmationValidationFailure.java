package com.miaoyu.ticket.agent.domain.confirmation;

/** 确认前的固定拒绝原因，应用层据此映射安全错误码和展示文案。 */
public enum AgentConfirmationValidationFailure {
    NOT_OWNER,
    ACTION_NOT_CONFIRMABLE,
    EXPIRED,
    RUN_ENDED,
    PLAN_CHANGED,
    NODE_NOT_WAITING_CONFIRMATION,
    PARAMETERS_CHANGED,
    BUSINESS_DATA_INVALID
}
