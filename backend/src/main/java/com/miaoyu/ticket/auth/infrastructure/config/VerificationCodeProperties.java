package com.miaoyu.ticket.auth.infrastructure.config;

import jakarta.validation.Valid;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/** 验证码安全、限流和邮件模板配置集中校验，禁止使用空摘要密钥启动。 */
@Validated
@ConfigurationProperties(prefix = "cinewise.auth.verification")
public record VerificationCodeProperties(
        @NotBlank String hashSecret,
        @Min(6) @Max(6) int digits,
        @NotNull Duration ttl,
        @NotNull Duration cooldown,
        @Min(1) int maximumAttempts,
        @NotNull Duration ipWindow,
        @Min(1) int maximumIpRequests,
        @Valid @NotNull Mail mail) {

    private static final int MINIMUM_SECRET_BYTES = 32;

    @AssertTrue(message = "验证码摘要密钥不得少于 32 字节")
    public boolean isHashSecretLengthValid() {
        return hashSecret != null
                && hashSecret.getBytes(StandardCharsets.UTF_8).length >= MINIMUM_SECRET_BYTES;
    }

    @AssertTrue(message = "验证码有效期、冷却和 IP 限流窗口必须大于零")
    public boolean areDurationsValid() {
        return isPositive(ttl) && isPositive(cooldown) && isPositive(ipWindow);
    }

    private static boolean isPositive(Duration duration) {
        return duration != null && !duration.isZero() && !duration.isNegative();
    }

    /** SMTP 关闭时允许不填发件人；开启后必须显式配置。 */
    public record Mail(boolean smtpEnabled, String from, @NotBlank String subject) {

        @AssertTrue(message = "启用 SMTP 验证码邮件时必须配置发件人")
        public boolean isFromConfiguredWhenEnabled() {
            return !smtpEnabled || (from != null && !from.isBlank());
        }
    }
}
