package com.miaoyu.ticket.agent.api;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.time.OffsetDateTime;

/** 管理端列表使用的脱敏运行摘要。 */
@JsonInclude(JsonInclude.Include.ALWAYS)
public record AdminAgentRunSummaryResponse(
        String runId,
        String sessionId,
        String userDisplay,
        String status,
        String planId,
        Integer planVersion,
        int nodeCount,
        int completedNodeCount,
        int failedNodeCount,
        OffsetDateTime startedAt,
        OffsetDateTime finishedAt,
        Long durationMs,
        Integer errorCode,
        String errorSummary) {
}
