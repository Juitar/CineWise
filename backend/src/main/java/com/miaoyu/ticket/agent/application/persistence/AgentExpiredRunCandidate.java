package com.miaoyu.ticket.agent.application.persistence;

/**
 * 到期清理所需的最小运行定位信息。
 *
 * <p>候选来自事件表和终态运行的连接查询；不携带消息、载荷或用户展示内容。</p>
 */
public record AgentExpiredRunCandidate(long runId, String externalRunId, long userId, String sessionId) {
}
