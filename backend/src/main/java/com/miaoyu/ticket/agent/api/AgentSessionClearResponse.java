package com.miaoyu.ticket.agent.api;

/** 单个会话逻辑清空结果。 */
public record AgentSessionClearResponse(String sessionId, boolean cleared) {
}
