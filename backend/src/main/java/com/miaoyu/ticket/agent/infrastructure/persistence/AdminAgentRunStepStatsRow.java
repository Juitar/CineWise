package com.miaoyu.ticket.agent.infrastructure.persistence;

/** 管理列表的步骤聚合行，不承载步骤或工具原始内容。 */
public record AdminAgentRunStepStatsRow(
        long runId,
        int nodeCount,
        int completedNodeCount,
        int failedNodeCount) {
}
