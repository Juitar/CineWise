package com.miaoyu.ticket.ticketing.application;

import java.time.LocalDateTime;
import org.springframework.stereotype.Service;

/**
 * 退票座位释放边界。
 *
 * <ul>
 *   <li>服务不接收任意seatIds，防止调用方扩大释放范围；</li>
 *   <li>订单ID只定位已持久化的订单座位快照；</li>
 *   <li>Repository的SOLD条件保证重复执行不产生第二次变化；</li>
 *   <li>退款事务负责把影响行数与订单票数比较并在不一致时回滚。</li>
 * </ul>
 * <p>订单模块只传订单ID，实际范围由订单座位快照与SOLD条件共同限定。</p>
 */
@Service
public class SeatRefundService {

    private final SeatRefundRepository repository;

    public SeatRefundService(SeatRefundRepository repository) {
        this.repository = repository;
    }

    /** 影响查询用计数发现历史不一致，但不改变任何座位。 */
    public int countSoldSeats(long orderId) {
        return repository.countSoldSeats(orderId);
    }

    /** 事务用影响行数确认没有部分释放或误释放。 */
    public int releaseSoldSeats(long orderId, LocalDateTime refundedAt) {
        return repository.releaseSoldSeats(orderId, refundedAt);
    }
}
