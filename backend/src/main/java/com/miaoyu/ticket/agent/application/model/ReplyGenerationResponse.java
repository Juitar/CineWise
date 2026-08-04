package com.miaoyu.ticket.agent.application.model;

import com.miaoyu.ticket.agent.application.reply.AgentReplyMessageType;
import com.miaoyu.ticket.agent.application.reply.AgentReplyPayload;
import java.util.Objects;

/** 模型网关返回给主控的内部结构化回复，不是 HTTP 或 SSE DTO。 */
public record ReplyGenerationResponse(
        String text, AgentReplyMessageType messageType, AgentReplyPayload payload) {

    public ReplyGenerationResponse {
        if (text == null || text.isBlank()) {
            throw new IllegalArgumentException("text 不能为空");
        }
        messageType = Objects.requireNonNull(messageType, "messageType 不能为空");
        payload = Objects.requireNonNull(payload, "payload 不能为空");
        if (!payload.supports(messageType)) {
            throw new IllegalArgumentException("回复类型与载荷不匹配");
        }
    }
}
