package com.miaoyu.ticket.ticketing.application;

import java.time.LocalDateTime;

/**
 * 票务模块向订单等调用方公开的最小场次上下文，不暴露仓储模型或持久化实现。
 *
 * <p>影院名称和区域仍由内容模块提供；本 DTO 只承载 A 权威拥有的场次关联与时间事实。</p>
 */
public record ShowContextView(long showId, long cinemaId, LocalDateTime startTime) {
}
