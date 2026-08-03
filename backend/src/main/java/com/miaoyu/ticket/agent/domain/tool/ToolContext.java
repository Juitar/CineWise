package com.miaoyu.ticket.agent.domain.tool;

import java.util.List;
import java.util.Objects;

/** Agent 调用工具时携带的最小运行上下文。 */
public record ToolContext(
        String runId,
        String nodeId,
        String targetName,
        List<String> inputRefs,
        long deadlineMs,
        String traceId,
        String clientRequestId,
        String idempotencyKey,
        Long stateVersion) {

    public ToolContext {
        requireText(runId, "runId");
        requireText(nodeId, "nodeId");
        requireText(targetName, "targetName");
        requireText(traceId, "traceId");
        if (deadlineMs <= 0L) {
            throw new IllegalArgumentException("deadlineMs 必须大于 0");
        }
        inputRefs = List.copyOf(Objects.requireNonNull(inputRefs, "inputRefs 不能为空"));
        inputRefs.forEach(inputRef -> requireText(inputRef, "inputRefs 元素"));
    }

    /** 写工具调用必须复用客户端请求标识和幂等键。 */
    public void requireWriteRequestIdentifiers() {
        requireText(clientRequestId, "clientRequestId");
        requireText(idempotencyKey, "idempotencyKey");
    }

    private static void requireText(String value, String fieldName) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(fieldName + " 不能为空");
        }
    }
}
