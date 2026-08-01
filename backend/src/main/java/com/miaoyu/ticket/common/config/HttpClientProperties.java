package com.miaoyu.ticket.common.config;

import jakarta.validation.constraints.NotNull;
import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/** 外部 HTTP 调用的公共超时基线；各 Provider 可以在自己的适配器中收紧。 */
@Validated
@ConfigurationProperties(prefix = "cinewise.http-client")
public record HttpClientProperties(@NotNull Duration connectTimeout, @NotNull Duration readTimeout) {

    public HttpClientProperties {
        if (connectTimeout.isNegative() || connectTimeout.isZero()) {
            throw new IllegalArgumentException("connectTimeout must be positive");
        }
        if (readTimeout.isNegative() || readTimeout.isZero()) {
            throw new IllegalArgumentException("readTimeout must be positive");
        }
    }
}
