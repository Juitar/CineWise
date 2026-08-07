package com.miaoyu.ticket.common.config;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/** 锁座和待支付时限；范围与A票务设计冻结值一致。 */
@Validated
@ConfigurationProperties(prefix = "cinewise.transaction")
public record TicketingTransactionProperties(
        @Min(5) @Max(30) int ticketLockMinutes,
        @Min(5) @Max(30) int orderPaymentMinutes,
        boolean expiryJobEnabled,
        @Min(1_000) @Max(300_000) int expiryJobDelayMilliseconds,
        @Min(1) @Max(100) int expiryBatchSize,
        boolean showEndInvalidationJobEnabled,
        @Min(1_000) @Max(300_000) int showEndInvalidationJobDelayMilliseconds,
        @Min(1) @Max(100) int showEndInvalidationBatchSize) {
}
