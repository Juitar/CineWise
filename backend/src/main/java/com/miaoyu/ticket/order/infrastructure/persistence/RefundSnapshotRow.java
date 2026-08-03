package com.miaoyu.ticket.order.infrastructure.persistence;

import java.time.LocalDateTime;

/**
 * refund_request的显式持久化投影。
 *
 * <ul>
 *   <li>数据库状态先保留字符串，适配器统一映射领域枚举；</li>
 *   <li>impactSnapshot保留原始JSON，适配器负责兼容JDBC表现；</li>
 *   <li>processedTime允许REQUESTED阶段为空；</li>
 *   <li>version和updatedAt用于恢复最新权威状态。</li>
 * </ul>
 * <p>持久化投影不会穿透到Application或REST DTO。</p>
 */
public record RefundSnapshotRow(
        long refundId,
        String refundNo,
        long orderId,
        long userId,
        String idempotencyKey,
        String actionId,
        String reason,
        String impactSnapshot,
        String status,
        LocalDateTime requestTime,
        LocalDateTime processedTime,
        int version,
        LocalDateTime updatedAt) {
}
