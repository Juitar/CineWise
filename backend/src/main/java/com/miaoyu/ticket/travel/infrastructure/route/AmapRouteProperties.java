package com.miaoyu.ticket.travel.infrastructure.route;

import java.time.Duration;
import java.util.Objects;
import org.springframework.boot.context.properties.ConfigurationProperties;

/** 高德路线的运行配置；未显式启用或未配置 Key 时不能请求第三方。 */
@ConfigurationProperties("cinewise.travel.route.amap")
public record AmapRouteProperties(
        boolean enabled, String key, Duration connectTimeout, Duration readTimeout, Duration resultTtl) {

    public AmapRouteProperties {
        key = key == null ? "" : key.trim();
        connectTimeout = requirePositive(connectTimeout, "connectTimeout");
        readTimeout = requirePositive(readTimeout, "readTimeout");
        resultTtl = requirePositive(resultTtl, "resultTtl");
    }

    private static Duration requirePositive(Duration value, String name) {
        Objects.requireNonNull(value, name + " must not be null");
        if (value.isZero() || value.isNegative()) {
            throw new IllegalArgumentException(name + " must be positive");
        }
        return value;
    }
}
