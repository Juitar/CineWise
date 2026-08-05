package com.miaoyu.ticket.auth.infrastructure.security;

import com.miaoyu.ticket.auth.application.RegistrationInviteHasher;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.util.HexFormat;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

/** 邀请码使用独立 HMAC 密钥，避免与 JWT、审计或验证码摘要互相影响。 */
final class HmacRegistrationInviteHasher implements RegistrationInviteHasher {

    private static final String ALGORITHM = "HmacSHA256";
    private final SecretKeySpec key;

    HmacRegistrationInviteHasher(String secret) {
        this.key = new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), ALGORITHM);
    }

    @Override
    public String hash(String inviteCode) {
        try {
            Mac mac = Mac.getInstance(ALGORITHM);
            mac.init(key);
            return HexFormat.of().formatHex(mac.doFinal(inviteCode.getBytes(StandardCharsets.UTF_8)));
        } catch (GeneralSecurityException exception) {
            throw new IllegalStateException("无法计算邀请码摘要", exception);
        }
    }
}
