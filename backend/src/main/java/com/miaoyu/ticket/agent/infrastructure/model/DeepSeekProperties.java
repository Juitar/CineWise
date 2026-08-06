package com.miaoyu.ticket.agent.infrastructure.model;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/** DeepSeek 运行配置只绑定环境变量，不提供可用的密钥默认值。 */
@Validated
@ConfigurationProperties(prefix = "cinewise.agent.deepseek")
public record DeepSeekProperties(
        boolean enabled,
        @NotBlank String apiKey,
        @NotBlank String model,
        @NotBlank String baseUrl,
        @NotNull Duration timeout) {

    public DeepSeekProperties {
        if (timeout.isZero() || timeout.isNegative()) {
            throw new IllegalArgumentException("DeepSeek timeout 必须大于 0");
        }
    }
}
