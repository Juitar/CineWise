package com.miaoyu.ticket.agent.application.model;

import com.miaoyu.ticket.agent.application.reply.AgentReplyMessageType;
import com.miaoyu.ticket.agent.application.reply.AgentReplyPayload;
import java.util.Objects;

/**
 * 模型网关返回给主控的内部结构化回复，不是 HTTP 或 SSE DTO。
 *
 * <p>保留 payload 是为了让后续输出层能够验证文案与结构化事实的对应关系；对外 API 是否输出、
 * 如何输出这些字段，必须由独立的 Controller/SSE change 决定。
 *
 * @param text 模型生成的展示文案，不能为空但不能被当作业务事实重新解析
 * @param messageType 服务端要求的消息类型，用于防止模型返回错卡片类别
 * @param payload 与消息类型匹配的受控事实，不能替换为任意 Map
 */
public record ReplyGenerationResponse(
        String text, AgentReplyMessageType messageType, AgentReplyPayload payload) {

    public ReplyGenerationResponse {
        if (text == null || text.isBlank()) {
            throw new IllegalArgumentException("text 不能为空");
        }
        messageType = Objects.requireNonNull(messageType, "messageType 不能为空");
        payload = Objects.requireNonNull(payload, "payload 不能为空");
        // 再次校验让任何 ModelGateway 实现都无法返回与事实类型不匹配的内部回复。
        if (!payload.supports(messageType)) {
            throw new IllegalArgumentException("回复类型与载荷不匹配");
        }
    }
}
