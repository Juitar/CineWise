package com.miaoyu.ticket.common.config;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * 公共定时任务线程池配置。
 *
 * <p>测试窗口可关闭整组自动任务，防止定时扫描和一次性人工验证并发写入同一批数据。</p>
 *
 * @param enabled 是否注册 `@Scheduled` 自动任务；默认开启
 */
@Validated
@ConfigurationProperties(prefix = "cinewise.scheduling")
public record SchedulingProperties(
        boolean enabled,
        @Min(1) @Max(16) int poolSize,
        @NotBlank String threadNamePrefix,
        @Min(0) @Max(120) int shutdownAwaitSeconds) {
}
