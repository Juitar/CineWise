package com.miaoyu.ticket.auth.infrastructure.config;

import jakarta.validation.Valid;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/** 认证密钥和 Cookie 属性集中校验，应用不得带空密钥或短密钥启动。 */
@Validated
@ConfigurationProperties(prefix = "cinewise.auth")
public record AuthProperties(
        @NotBlank String jwtSecret,
        @NotBlank String auditHashSecret,
        @NotNull Duration accessTokenTtl,
        @NotNull Duration renewalThreshold,
        @NotNull Duration absoluteSessionTtl,
        @NotBlank String accessCookieName,
        @NotBlank String csrfCookieName,
        @NotBlank String csrfHeaderName,
        boolean cookieSecure,
        @NotBlank String cookieSameSite,
        @Min(1) int loginLogRetentionDays,
        @Min(1) long loginLogCleanupDelayMilliseconds,
        @Valid @NotNull DemoSeed demoSeed) {

    private static final int MIN_SECRET_BYTES = 32;

    @AssertTrue(message = "JWT 和审计摘要密钥均不得少于 32 字节")
    public boolean isSecretLengthValid() {
        return byteLength(jwtSecret) >= MIN_SECRET_BYTES && byteLength(auditHashSecret) >= MIN_SECRET_BYTES;
    }

    @AssertTrue(message = "JWT 有效期、续期阈值和最长连续登录时间必须大于零")
    public boolean isSessionDurationsPositive() {
        return isPositive(accessTokenTtl)
                && isPositive(renewalThreshold)
                && isPositive(absoluteSessionTtl);
    }

    @AssertTrue(message = "续期阈值必须小于 JWT 有效期，最长连续登录时间不得小于 JWT 有效期")
    public boolean isSessionDurationsConsistent() {
        if (accessTokenTtl == null || renewalThreshold == null || absoluteSessionTtl == null) {
            return true;
        }
        return renewalThreshold.compareTo(accessTokenTtl) < 0
                && absoluteSessionTtl.compareTo(accessTokenTtl) >= 0;
    }

    private static boolean isPositive(Duration duration) {
        return duration != null && !duration.isZero() && !duration.isNegative();
    }

    private static int byteLength(String value) {
        return value == null ? 0 : value.getBytes(StandardCharsets.UTF_8).length;
    }

    /** 演示种子关闭时允许留空；开启后所有字段必须显式提供，防止生成已知默认凭据。 */
    public record DemoSeed(
            boolean enabled,
            String userEmail,
            String userPassword,
            String userNickname,
            String adminEmail,
            String adminPassword,
            String adminNickname,
            String privacyPolicyVersion) {

        @AssertTrue(message = "启用认证演示种子时必须完整配置用户和管理员账号")
        public boolean isCompleteWhenEnabled() {
            return !enabled || allPresent(
                    userEmail,
                    userPassword,
                    userNickname,
                    adminEmail,
                    adminPassword,
                    adminNickname,
                    privacyPolicyVersion);
        }

        private static boolean allPresent(String... values) {
            for (String value : values) {
                if (value == null || value.isBlank()) {
                    return false;
                }
            }
            return true;
        }
    }
}
