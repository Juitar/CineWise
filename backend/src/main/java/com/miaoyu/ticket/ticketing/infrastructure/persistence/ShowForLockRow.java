package com.miaoyu.ticket.ticketing.infrastructure.persistence;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/** 建单事务中重新校验的场次权威投影。 */
public record ShowForLockRow(
        long showId,
        BigDecimal basePrice,
        String status,
        LocalDateTime startTime) {
}
