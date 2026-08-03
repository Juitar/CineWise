package com.miaoyu.ticket.order.infrastructure.persistence;

import java.time.LocalDateTime;

/**
 * 退款写入参数。
 *
 * <ul>
 *   <li>所有ID由应用层雪花生成，不依赖数据库自增；</li>
 *   <li>actionId在传统页面路径保持null；</li>
 *   <li>reason只保存已限制长度的规范化文本；</li>
 *   <li>impactSnapshot在持久化适配器边界序列化为合法JSON。</li>
 * </ul>
 */
record RefundInsertRow(
        long refundId,
        String refundNo,
        long orderId,
        long userId,
        String idempotencyKey,
        String actionId,
        String reason,
        String impactSnapshot,
        LocalDateTime requestedAt) {
}
