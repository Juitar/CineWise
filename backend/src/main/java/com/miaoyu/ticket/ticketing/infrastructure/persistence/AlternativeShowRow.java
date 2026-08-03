package com.miaoyu.ticket.ticketing.infrastructure.persistence;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 替代场次只读投影。
 *
 * <ul>
 *   <li>票价和状态来自movie_show；</li>
 *   <li>余座来自查询时show_seat聚合；</li>
 *   <li>内容展示字段由调用方另行通过D公开端口补齐。</li>
 * </ul>
 */
public record AlternativeShowRow(
        long showId,
        long cinemaId,
        LocalDateTime startTime,
        BigDecimal basePrice,
        String status,
        int availableSeatCount) {
}
