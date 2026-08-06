package com.miaoyu.ticket.agent.domain.persistence;

/** V008 中可展示消息的固定类型，不包含 SSE 流式或订单类消息。 */
public enum AgentMessageType {
    TEXT,
    QUESTION,
    MOVIE_CARD,
    PLAN_CARD,
    SELECT_SEATS,
    PROGRESS,
    ERROR
}
