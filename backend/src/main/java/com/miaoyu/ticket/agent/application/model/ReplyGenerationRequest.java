package com.miaoyu.ticket.agent.application.model;

import com.miaoyu.ticket.agent.application.reply.AgentReplyMessageType;
import com.miaoyu.ticket.agent.application.reply.AgentReplyPayload;
import java.util.Objects;

/** 只把当前请求和已经过服务端筛选的回复事实交给模型网关。 */
public record ReplyGenerationRequest(
        String clientRequestId,
        String input,
        AgentReplyMessageType requestedType,
        AgentReplyPayload payload) {

    public ReplyGenerationRequest {
        if (clientRequestId == null || clientRequestId.isBlank()) {
            throw new IllegalArgumentException("clientRequestId 不能为空");
        }
        if (input == null || input.isBlank()) {
            throw new IllegalArgumentException("input 不能为空");
        }
        requestedType = Objects.requireNonNull(requestedType, "requestedType 不能为空");
        payload = Objects.requireNonNull(payload, "payload 不能为空");
        if (!payload.supports(requestedType)) {
            throw new IllegalArgumentException("回复类型与载荷不匹配");
        }
    }
}
