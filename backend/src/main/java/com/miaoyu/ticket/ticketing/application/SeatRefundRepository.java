package com.miaoyu.ticket.ticketing.application;

import java.time.LocalDateTime;

/**
 * 已售座位退回可售状态的数据库条件更新端口。
 *
 * <ul>
 *   <li>资源范围来自不可变的ticket_order_seat快照；</li>
 *   <li>当前状态必须仍为SOLD，避免覆盖其他交易状态；</li>
 *   <li>计数用于影响查询提前发现历史不一致；</li>
 *   <li>更新影响行数用于事务判断是否完整释放。</li>
 * </ul>
 */
public interface SeatRefundRepository {

    /** 只统计订单明细引用且当前仍为SOLD的座位。 */
    int countSoldSeats(long orderId);

    /** 只释放订单明细引用且当前仍为SOLD的座位。 */
    int releaseSoldSeats(long orderId, LocalDateTime refundedAt);
}
