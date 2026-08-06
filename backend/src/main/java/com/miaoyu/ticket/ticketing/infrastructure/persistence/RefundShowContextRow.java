package com.miaoyu.ticket.ticketing.infrastructure.persistence;

import java.time.LocalDateTime;

/**
 * 原订单场次的退票判断投影。
 *
 * <p>只包含A判断退票截止和查询同影院候选所需字段，不携带D拥有的内容摘要。</p>
 */
public record RefundShowContextRow(
        long showId,
        long movieId,
        long cinemaId,
        LocalDateTime startTime) {
}
