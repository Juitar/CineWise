package com.miaoyu.ticket.ticketing.application;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 推荐模块可消费的最小票务场次事实。
 *
 * <p>price 始终保留两位小数；expiresAt 只表示候选截止时间，不承诺价格或余座在此之前不变。</p>
 */
public record SaleableShowView(
        long showId,
        long movieId,
        long cinemaId,
        BigDecimal price,
        LocalDateTime startTime,
        LocalDateTime endTime,
        LocalDateTime expiresAt,
        String source,
        boolean saleable,
        int availableSeatCount,
        int stateVersion,
        LocalDateTime updatedAt) {
}
