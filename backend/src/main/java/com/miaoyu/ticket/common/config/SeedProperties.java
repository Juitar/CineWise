package com.miaoyu.ticket.common.config;

import jakarta.validation.constraints.Positive;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/** 固定演示数据的显式开关与随机种子；默认关闭，避免启动时意外写入共享数据库。 */
@Validated
@ConfigurationProperties(prefix = "cinewise.seed")
public record SeedProperties(boolean enabled, @Positive long fixedValue) {
}
