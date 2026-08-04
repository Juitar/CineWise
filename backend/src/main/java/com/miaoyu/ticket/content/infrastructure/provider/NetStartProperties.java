package com.miaoyu.ticket.content.infrastructure.provider;

import java.time.Duration;
import java.util.Objects;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * NetStart 学习 Provider 的运行开关和本地保护参数。
 *
 * <p>这些值是本项目对第三方的保守保护，不代表对方公布的配额。默认关闭，正式环境也必须由
 * {@link NetStartContentProvider} 再次拦截，避免仅靠部署约定误开学习接口。</p>
 */
@ConfigurationProperties("cinewise.content.netstart")
public record NetStartProperties(boolean enabled, String baseUrl, String dailySyncCron,
                                 Duration connectTimeout, Duration readTimeout,
                                 int requestsPerMinute, int retryCount, Duration retryBackoff) {

    /** 启动即拒绝危险参数，防止限流、超时或重试在运行时失效。 */
    public NetStartProperties {
        baseUrl = requireText(baseUrl, "baseUrl");
        dailySyncCron = requireText(dailySyncCron, "dailySyncCron");
        connectTimeout = requirePositive(connectTimeout, "connectTimeout");
        readTimeout = requirePositive(readTimeout, "readTimeout");
        retryBackoff = requirePositive(retryBackoff, "retryBackoff");
        if (requestsPerMinute != 10 || retryCount != 1) {
            throw new IllegalArgumentException("NetStart only permits 10 req/min and one retry");
        }
    }

    private static String requireText(String value, String name) {
        String normalized = Objects.requireNonNull(value, name + " must not be null").trim();
        if (normalized.isEmpty()) {
            throw new IllegalArgumentException(name + " must not be blank");
        }
        return normalized;
    }

    private static Duration requirePositive(Duration value, String name) {
        value = Objects.requireNonNull(value, name + " must not be null");
        if (value.isZero() || value.isNegative()) {
            throw new IllegalArgumentException(name + " must be positive");
        }
        return value;
    }
}
