package com.miaoyu.ticket.ticketing.infrastructure.persistence;

import java.time.LocalDateTime;

/**
 * 支付成功事件所需的最小场次持久化投影，不包含D拥有的影院内容。
 * cinemaId只作为调用公开内容端口的关联键，不能用于直接查询D的持久化实现。
 */
public record ShowContextRow(long showId, long cinemaId, LocalDateTime startTime) {
}
