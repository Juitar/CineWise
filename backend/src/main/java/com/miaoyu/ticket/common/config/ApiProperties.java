package com.miaoyu.ticket.common.config;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/** REST 公共分页边界。 */
@Validated
@ConfigurationProperties(prefix = "cinewise.api")
public record ApiProperties(
        @Min(1) int defaultPage,
        @Min(1) @Max(100) int defaultPageSize,
        @Min(1) @Max(500) int maxPageSize) {
}
