package com.miaoyu.ticket.agent.domain.confirmation;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

/** 一个 action 仅有一组稳定写标识，结果未知时必须原样复用。 */
public record AgentActionWriteIdentifiers(String clientRequestId, String idempotencyKey) {
    private static final int KEY_HASH_LENGTH = 48;

    public AgentActionWriteIdentifiers {
        requireKey(clientRequestId, "clientRequestId");
        requireKey(idempotencyKey, "idempotencyKey");
    }

    public static AgentActionWriteIdentifiers forAction(String actionId) {
        if (actionId == null || actionId.isBlank()) {
            throw new IllegalArgumentException("actionId 不能为空");
        }
        return new AgentActionWriteIdentifiers(
                "agent-act-" + digest("client-request:" + actionId),
                "agent-order-" + digest("idempotency:" + actionId));
    }

    private static String digest(String source) {
        try {
            String hash = HexFormat.of().formatHex(
                    MessageDigest.getInstance("SHA-256").digest(source.getBytes(StandardCharsets.UTF_8)));
            return hash.substring(0, KEY_HASH_LENGTH);
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("JDK 缺少 SHA-256", exception);
        }
    }

    private static void requireKey(String value, String fieldName) {
        if (value == null || value.isBlank() || value.length() > 64) {
            throw new IllegalArgumentException(fieldName + " 必须为 1 至 64 个字符");
        }
    }
}
