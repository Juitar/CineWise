package com.miaoyu.ticket.travel.application;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/** 餐饮查询半径由部署配置控制，防止调用方无限扩大第三方查询范围。 */
@Validated
@ConfigurationProperties(prefix = "cinewise.travel.food")
public record FoodQueryProperties(
        @Min(100) @Max(5_000) int radiusDefaultMeters,
        @Min(100) @Max(5_000) int radiusMinMeters,
        @Min(100) @Max(5_000) int radiusMaxMeters) {

    public FoodQueryProperties {
        if (radiusMinMeters > radiusDefaultMeters || radiusDefaultMeters > radiusMaxMeters) {
            throw new IllegalArgumentException("餐饮半径配置必须满足最小值 <= 默认值 <= 最大值");
        }
    }
}
