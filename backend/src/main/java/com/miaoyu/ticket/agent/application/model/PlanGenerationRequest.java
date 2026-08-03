package com.miaoyu.ticket.agent.application.model;

import java.util.Objects;
import java.util.Set;

/** 应用层向模型网关发出的候选计划请求。 */
public record PlanGenerationRequest(String clientRequestId, String input, Set<String> allowedToolNames) {

    public PlanGenerationRequest {
        if (clientRequestId == null || clientRequestId.isBlank()) {
            throw new IllegalArgumentException("clientRequestId 不能为空");
        }
        Objects.requireNonNull(input, "input 不能为空");
        allowedToolNames = allowedToolNames == null ? Set.of() : Set.copyOf(allowedToolNames);
    }
}
