package com.miaoyu.ticket.ticketing.application;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 单个可售场次的应用层只读模型。
 *
 * <p>expiresAt是该场次作为可购候选的截止时刻，不是价格或余座快照的有效期。</p>
 */
public record ShowSummaryView(
        long showId,
        long movieId,
        long cinemaId,
        String cinemaName,
        long auditoriumId,
        String auditoriumName,
        LocalDateTime startTime,
        LocalDateTime endTime,
        LocalDateTime expiresAt,
        String languageVersion,
        BigDecimal basePrice,
        int availableSeatCount,
        String status,
        String dataType,
        int stateVersion,
        LocalDateTime updatedAt) {
}
