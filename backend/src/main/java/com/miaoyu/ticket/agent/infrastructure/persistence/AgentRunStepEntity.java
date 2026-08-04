package com.miaoyu.ticket.agent.infrastructure.persistence;

import java.time.LocalDateTime;

/** `agent_run_step` 的持久化行；JSON 列只保留已校验计划和脱敏槽位快照。 */
public record AgentRunStepEntity(
        long id,
        long runId,
        int planVersion,
        String nodeId,
        String nodeType,
        String dependsOnJson,
        String inputRefsJson,
        String status,
        String failurePolicy,
        int attemptCount,
        int retryCount,
        boolean recoveryPending,
        boolean autoSkipped,
        String skipReason,
        String skipSourceNodeId,
        String slotSnapshotJson,
        LocalDateTime startedAt,
        LocalDateTime finishedAt,
        long version,
        LocalDateTime createTime,
        LocalDateTime updateTime,
        LocalDateTime expireAt) {
}
