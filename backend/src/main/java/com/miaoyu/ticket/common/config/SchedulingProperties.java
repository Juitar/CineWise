package com.miaoyu.ticket.common.config;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/** 公共定时任务线程池配置。 */
@Validated
@ConfigurationProperties(prefix = "cinewise.scheduling")
public record SchedulingProperties(
        @Min(1) @Max(16) int poolSize,
        @NotBlank String threadNamePrefix,
        @Min(0) @Max(120) int shutdownAwaitSeconds) {
}
