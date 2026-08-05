package com.miaoyu.ticket.ticketing.application;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 推荐模块可消费的最小票务场次事实。
 *
 * <p>dataAt 是本次查询生成的票务快照时刻；expiresAt 只表示推荐候选截止时间，
 * 不承诺价格或余座在此之前不变。</p>
 */
public record SaleableShowView(
        long showId,
        long movieId,
        long cinemaId,
        BigDecimal price,
        LocalDateTime startTime,
        LocalDateTime endTime,
        String dataType,
        String source,
        LocalDateTime dataAt,
        LocalDateTime expiresAt,
        boolean saleable,
        int availableSeatCount,
        int stateVersion,
        LocalDateTime updatedAt) {
}
