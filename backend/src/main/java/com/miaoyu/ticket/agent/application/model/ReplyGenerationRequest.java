package com.miaoyu.ticket.agent.application.model;

import java.util.Objects;

/** 应用层向模型网关发出的结构化回复生成请求。 */
public record ReplyGenerationRequest(String clientRequestId, String input) {

    public ReplyGenerationRequest {
        if (clientRequestId == null || clientRequestId.isBlank()) {
            throw new IllegalArgumentException("clientRequestId 不能为空");
        }
        Objects.requireNonNull(input, "input 不能为空");
    }
}
