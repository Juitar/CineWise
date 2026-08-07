package com.miaoyu.ticket.agent.application.audit;

import java.time.LocalDateTime;
import java.util.List;

/** 管理页面的脱敏运行投影；不保存或携带消息、工具入参、事件原文。 */
public record AdminAgentRunView(
        String runId,
        String sessionId,
        String userDisplay,
        String status,
        String planId,
        Integer planVersion,
        int nodeCount,
        int completedNodeCount,
        int failedNodeCount,
        LocalDateTime startedAt,
        LocalDateTime finishedAt,
        Long durationMs,
        Integer errorCode,
        String errorSummary,
        List<NodeView> nodes) {

    public record NodeView(
            String nodeId,
            String nodeType,
            String targetName,
            String status,
            int attemptCount,
            LocalDateTime startedAt,
            LocalDateTime finishedAt,
            Long durationMs,
            String toolStatus,
            Integer errorCode,
            String errorSummary,
            String recoveryHint) {
    }
}
