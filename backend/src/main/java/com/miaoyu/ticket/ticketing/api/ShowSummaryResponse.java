package com.miaoyu.ticket.ticketing.api;

import java.time.OffsetDateTime;

/**
 * 已冻结的单个可选场次 REST 响应契约。
 *
 * <p>expiresAt用于排除已到开场时间的候选，不承诺价格、余座或场次状态此前保持不变。</p>
 */
public record ShowSummaryResponse(
        String showId,
        String movieId,
        String cinemaId,
        String cinemaName,
        String auditoriumId,
        String auditoriumName,
        OffsetDateTime startTime,
        OffsetDateTime endTime,
        OffsetDateTime expiresAt,
        String languageVersion,
        String basePrice,
        int availableSeatCount,
        String status,
        String dataType,
        int stateVersion,
        OffsetDateTime updatedAt) {
}
