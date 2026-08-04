package com.miaoyu.ticket.agent.application.tool;

import com.miaoyu.ticket.agent.domain.run.ExecutionRunState;
import java.util.Objects;

/** B 的运行用例提供的最小可信调用元数据，不包含模型槽位或用户业务参数。 */
public record RankMoviePlanExecutionRequest(
        ExecutionRunState state, String nodeId, String runId, String traceId, long remainingDeadlineMs) {

    public RankMoviePlanExecutionRequest {
        state = Objects.requireNonNull(state, "运行状态不能为空");
        requireText(nodeId, "nodeId");
        requireText(runId, "runId");
        requireText(traceId, "traceId");
        if (remainingDeadlineMs <= 0L) {
            throw new IllegalArgumentException("remainingDeadlineMs 必须大于 0");
        }
    }

    private static void requireText(String value, String fieldName) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(fieldName + " 不能为空");
        }
    }
}
