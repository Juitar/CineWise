package com.miaoyu.ticket.agent.application.tool;

import com.miaoyu.ticket.agent.domain.run.ExecutionRunState;
import java.util.Objects;

/** B 的运行用例提供的最小可信调用元数据，不包含模型槽位或用户业务参数。 */
public record RankMoviePlanExecutionRequest(
        ExecutionRunState state, String nodeId, String runId, String traceId, long remainingDeadlineMs,
        String distanceContextId, String distancePreference) {

    /** 普通推荐不携带距离上下文，保持既有调用方与行为不变。 */
    public RankMoviePlanExecutionRequest(
            ExecutionRunState state, String nodeId, String runId, String traceId, long remainingDeadlineMs) {
        this(state, nodeId, runId, traceId, remainingDeadlineMs, null, null);
    }

    public RankMoviePlanExecutionRequest {
        state = Objects.requireNonNull(state, "运行状态不能为空");
        requireText(nodeId, "nodeId");
        requireText(runId, "runId");
        requireText(traceId, "traceId");
        if (remainingDeadlineMs <= 0L) {
            throw new IllegalArgumentException("remainingDeadlineMs 必须大于 0");
        }
        // 此处只接受已由 B 受控恢复流程提供的上下文；ToolContext 统一校验 UUID 与 NEAREST 组合。
        if ((distanceContextId == null) != (distancePreference == null)) {
            throw new IllegalArgumentException("距离上下文字段必须同时提供或同时为空");
        }
    }

    private static void requireText(String value, String fieldName) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(fieldName + " 不能为空");
        }
    }
}
