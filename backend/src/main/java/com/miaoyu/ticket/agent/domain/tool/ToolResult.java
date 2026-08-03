package com.miaoyu.ticket.agent.domain.tool;

import java.time.Instant;
import java.util.Objects;

/** Agent 工具返回的统一公共包装，业务字段仅存放在 data 中。 */
public record ToolResult<T>(
        ToolStatus status,
        T data,
        Integer errorCode,
        boolean retryable,
        boolean replanSuggested,
        String suggestedNextAction,
        boolean degraded,
        String fallbackType,
        Long stateVersion,
        Instant dataAt,
        Instant expiresAt) {

    public ToolResult {
        Objects.requireNonNull(status, "status 不能为空");
        if (status == ToolStatus.SUCCESS && errorCode != null) {
            throw new IllegalArgumentException("成功结果不能包含 errorCode");
        }
        if ((dataAt == null) != (expiresAt == null)) {
            throw new IllegalArgumentException("dataAt 和 expiresAt 必须同时存在或同时为空");
        }
        if (dataAt != null && !expiresAt.isAfter(dataAt)) {
            throw new IllegalArgumentException("expiresAt 必须晚于 dataAt");
        }
    }

    /** 判断结果是否带有可供后续节点校验的动态数据时效。 */
    public boolean hasFreshnessWindow() {
        return dataAt != null && expiresAt != null;
    }
}
