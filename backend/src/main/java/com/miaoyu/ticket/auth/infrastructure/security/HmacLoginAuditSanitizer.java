package com.miaoyu.ticket.auth.infrastructure.security;

import com.miaoyu.ticket.auth.application.LoginAuditSanitizer;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.util.HexFormat;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

/** IP 使用带服务端密钥的 HMAC 摘要，无法通过普通字典直接还原。 */
final class HmacLoginAuditSanitizer implements LoginAuditSanitizer {

    private static final int MAX_USER_AGENT_INPUT_LENGTH = 1024;
    private final SecretKeySpec key;

    HmacLoginAuditSanitizer(String secret) {
        this.key = new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256");
    }

    @Override
    public String hashIp(String remoteAddress) {
        if (remoteAddress == null || remoteAddress.isBlank()) {
            return null;
        }
        return hmac(remoteAddress.strip());
    }

    @Override
    public String summarizeUserAgent(String userAgent) {
        if (userAgent == null || userAgent.isBlank()) {
            return null;
        }
        String cleaned = userAgent.replaceAll("[\\p{Cntrl}]", " ").replaceAll("\\s+", " ").strip();
        String bounded = cleaned.length() <= MAX_USER_AGENT_INPUT_LENGTH
                ? cleaned
                : cleaned.substring(0, MAX_USER_AGENT_INPUT_LENGTH);
        // User-Agent 同样只存 HMAC 摘要，短浏览器标识也不能以明文落库。
        return hmac(bounded);
    }

    private String hmac(String value) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(key);
            return HexFormat.of().formatHex(mac.doFinal(value.getBytes(StandardCharsets.UTF_8)));
        } catch (GeneralSecurityException exception) {
            throw new IllegalStateException("无法生成登录审计摘要", exception);
        }
    }
}
