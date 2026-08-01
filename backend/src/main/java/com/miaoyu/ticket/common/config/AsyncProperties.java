package com.miaoyu.ticket.common.config;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/** 非核心异步任务使用的有界线程池配置。 */
@Validated
@ConfigurationProperties(prefix = "cinewise.async")
public record AsyncProperties(
        @Min(1) @Max(16) int corePoolSize,
        @Min(1) @Max(32) int maxPoolSize,
        @Min(0) @Max(1000) int queueCapacity,
        @NotBlank String threadNamePrefix,
        @Min(0) @Max(120) int shutdownAwaitSeconds) {

    public AsyncProperties {
        if (maxPoolSize < corePoolSize) {
            throw new IllegalArgumentException("maxPoolSize must be greater than or equal to corePoolSize");
        }
    }
}
