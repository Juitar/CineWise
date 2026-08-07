package com.miaoyu.ticket.agent.api;
/** C 的初始化及超时恢复响应；不暴露距离上下文。 */
public record AgentDistanceRunResponse(String runId, String status, String lastEventId) { }
