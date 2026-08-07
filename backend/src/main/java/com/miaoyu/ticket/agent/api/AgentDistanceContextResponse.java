package com.miaoyu.ticket.agent.api;
import java.time.Instant;
/** 一次性上下文仅经普通 REST 返回给当前页面，不进入 SSE。 */
public record AgentDistanceContextResponse(String distanceContextId, Instant expiresAt, String distancePreference) { }
