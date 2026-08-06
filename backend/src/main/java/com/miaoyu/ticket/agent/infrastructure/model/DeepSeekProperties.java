package com.miaoyu.ticket.agent.infrastructure.model;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

/** DeepSeek 运行配置只绑定环境变量，不提供可用的密钥默认值。 */
@ConfigurationProperties(prefix = "cinewise.agent.deepseek")
public record DeepSeekProperties(
        boolean enabled,
        String apiKey,
        String model,
        String baseUrl,
        Duration timeout) {

    public DeepSeekProperties {
        if (timeout == null) {
            throw new IllegalArgumentException("DeepSeek timeout 不能为空");
        }
        if (timeout.isZero() || timeout.isNegative()) {
            throw new IllegalArgumentException("DeepSeek timeout 必须大于 0");
        }
    }

    /** 只有真实模型被显式开启时才要求部署密钥，默认 Mock 不依赖 Secret。 */
    public void requireEnabledConfiguration() {
        if (!enabled) {
            throw new IllegalStateException("未开启 DeepSeek 时不应创建真实模型网关");
        }
        requireText(apiKey, "DEEPSEEK_API_KEY");
        requireText(model, "DEEPSEEK_MODEL");
        requireText(baseUrl, "DEEPSEEK_BASE_URL");
    }

    private static void requireText(String value, String name) {
        if (value == null || value.isBlank()) {
            throw new IllegalStateException("启用 DeepSeek 时必须配置 " + name);
        }
    }
}
