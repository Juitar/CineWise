package com.miaoyu.ticket.agent.api;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.util.List;
import java.time.OffsetDateTime;

/** 管理端单运行详情，仅包含安全步骤摘要。 */
@JsonInclude(JsonInclude.Include.ALWAYS)
public record AdminAgentRunDetailResponse(
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
        String errorSummary,
        List<NodeResponse> nodes) {

    public record NodeResponse(
            String nodeId,
            String nodeType,
            String targetName,
            String status,
            int attemptCount,
            OffsetDateTime startedAt,
            OffsetDateTime finishedAt,
            Long durationMs,
            String toolStatus,
            Integer errorCode,
            String errorSummary,
            String recoveryHint) {
    }
}
