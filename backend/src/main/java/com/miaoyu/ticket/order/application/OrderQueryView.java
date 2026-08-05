package com.miaoyu.ticket.order.application;

import com.miaoyu.ticket.order.domain.OrderStatus;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 个人订单页面的权威只读视图。
 *
 * <p>场次上下文来自A的movie_show；影片标题、海报和影院名称仍由D的公开内容接口提供。</p>
 */
public record OrderQueryView(
        long orderId,
        String orderNo,
        long showId,
        long movieId,
        long cinemaId,
        LocalDateTime showStartTime,
        List<Long> seatIds,
        int ticketCount,
        BigDecimal unitPrice,
        BigDecimal totalAmount,
        OrderStatus status,
        LocalDateTime expireTime,
        int stateVersion,
        LocalDateTime updatedAt) {

    public OrderQueryView {
        seatIds = List.copyOf(seatIds);
    }
}
