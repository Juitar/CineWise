package com.miaoyu.ticket.auth.infrastructure.config;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.nio.charset.StandardCharsets;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/** 注册安全配置缺失时拒绝启动，防止邀请码以空密钥摘要或接受任意隐私版本。 */
@Validated
@ConfigurationProperties(prefix = "cinewise.auth.registration")
public record RegistrationProperties(
        @NotBlank String inviteHashSecret,
        @NotBlank String currentPrivacyPolicyVersion,
        @NotNull InitialInvite initialInvite) {

    private static final int MINIMUM_SECRET_BYTES = 32;

    @AssertTrue(message = "邀请码摘要密钥不得少于 32 字节")
    public boolean isInviteHashSecretLengthValid() {
        return inviteHashSecret != null
                && inviteHashSecret.getBytes(StandardCharsets.UTF_8).length >= MINIMUM_SECRET_BYTES;
    }

    /** 单邀请码初始化默认关闭；开启后必须一次给齐全部私有配置。 */
    public record InitialInvite(
            boolean enabled,
            String code,
            int maxUses,
            String validFrom,
            String expireTime) {

        @AssertTrue(message = "启用首个邀请码初始化时必须完整配置邀请码、次数和有效期")
        public boolean isCompleteWhenEnabled() {
            return !enabled
                    || (code != null
                            && !code.isBlank()
                            && code.length() <= 128
                            && maxUses > 0
                            && validFrom != null
                            && !validFrom.isBlank()
                            && expireTime != null
                            && !expireTime.isBlank());
        }
    }
}
