package com.miaoyu.ticket.auth.application.mail;

import java.util.Map;

/** 调用方只提供认证用户 ID，不得把完整邮箱带过模块边界。 */
public record EmailDeliveryCommand(
        String deliveryKey,
        String templateCode,
        String recipientUserId,
        Map<String, String> variables,
        String traceId) {

    public EmailDeliveryCommand {
        variables = variables == null ? Map.of() : Map.copyOf(variables);
    }

    @Override
    public String toString() {
        return "EmailDeliveryCommand[deliveryKey=[REDACTED], templateCode=" + templateCode
                + ", recipientUserId=[REDACTED], variables=[REDACTED], traceId=" + traceId + "]";
    }
}
