package com.miaoyu.ticket.agent.domain.confirmation;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

/** 一个 action 仅有一组稳定写标识，结果未知时必须原样复用。 */
public record AgentActionWriteIdentifiers(String clientRequestId, String idempotencyKey) {
    private static final int KEY_HASH_LENGTH = 48;
    private static final int MAX_CLIENT_REQUEST_ID_LENGTH = 64;
    private static final int MAX_IDEMPOTENCY_KEY_LENGTH = 128;

    public AgentActionWriteIdentifiers {
        requireKey(clientRequestId, "clientRequestId", MAX_CLIENT_REQUEST_ID_LENGTH);
        requireKey(idempotencyKey, "idempotencyKey", MAX_IDEMPOTENCY_KEY_LENGTH);
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

    private static void requireKey(String value, String fieldName, int maxLength) {
        if (value == null || value.isBlank() || value.length() > maxLength) {
            throw new IllegalArgumentException(fieldName + " 长度不合法");
        }
    }
}
