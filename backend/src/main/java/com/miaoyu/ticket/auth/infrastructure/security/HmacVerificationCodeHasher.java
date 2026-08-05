package com.miaoyu.ticket.auth.infrastructure.security;

import com.miaoyu.ticket.auth.application.VerificationCodeHasher;
import com.miaoyu.ticket.auth.domain.VerificationPurpose;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.util.HexFormat;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

/** HMAC 输入包含邮箱和用途，避免同一验证码摘要跨账号或跨用途复用。 */
public class HmacVerificationCodeHasher implements VerificationCodeHasher {

    private static final String ALGORITHM = "HmacSHA256";
    private final SecretKeySpec key;

    public HmacVerificationCodeHasher(String secret) {
        this.key = new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), ALGORITHM);
    }

    @Override
    public String hash(String normalizedEmail, VerificationPurpose purpose, String code) {
        try {
            Mac mac = Mac.getInstance(ALGORITHM);
            mac.init(key);
            byte[] digest = mac.doFinal(
                    (normalizedEmail + "\n" + purpose.name() + "\n" + code).getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (GeneralSecurityException exception) {
            throw new IllegalStateException("无法计算验证码摘要", exception);
        }
    }

    @Override
    public boolean matches(
            String normalizedEmail, VerificationPurpose purpose, String code, String expectedHash) {
        byte[] actual = hash(normalizedEmail, purpose, code).getBytes(StandardCharsets.US_ASCII);
        byte[] expected = expectedHash.getBytes(StandardCharsets.US_ASCII);
        return MessageDigest.isEqual(actual, expected);
    }
}
