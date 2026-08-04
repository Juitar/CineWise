package com.miaoyu.ticket.agent.application.model;

import java.util.Map;
import java.util.Set;

/** 应用层向模型网关发出的候选计划请求，只携带本次任务已确认的槽位。 */
public record PlanGenerationRequest(
        String clientRequestId,
        String input,
        Map<String, String> confirmedSlots,
        Set<String> allowedToolNames) {

    public PlanGenerationRequest {
        if (clientRequestId == null || clientRequestId.isBlank()) {
            throw new IllegalArgumentException("clientRequestId 不能为空");
        }
        if (input == null || input.isBlank()) {
            throw new IllegalArgumentException("input 不能为空");
        }
        confirmedSlots = confirmedSlots == null ? Map.of() : Map.copyOf(confirmedSlots);
        allowedToolNames = allowedToolNames == null ? Set.of() : Set.copyOf(allowedToolNames);
    }
}
