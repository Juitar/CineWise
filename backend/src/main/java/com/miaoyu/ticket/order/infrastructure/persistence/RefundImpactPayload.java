package com.miaoyu.ticket.order.infrastructure.persistence;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * V003 impact_snapshot的稳定JSON结构。
 *
 * <ul>
 *   <li>clientRequestId、refundReason和actionId用于原键参数一致性比较；</li>
 *   <li>refundAmount记录创建退款时的订单权威金额；</li>
 *   <li>orderVersion和ticketVersion记录影响确认对应的状态版本；</li>
 *   <li>showStartTime记录退款资格判断采用的截止事实。</li>
 * </ul>
 * <p>快照不包含JWT、Cookie、用户输入确认内容或其他模块私有数据。</p>
 */
record RefundImpactPayload(
        String clientRequestId,
        String refundReason,
        String actionId,
        BigDecimal refundAmount,
        int orderVersion,
        int ticketVersion,
        LocalDateTime showStartTime) {
}
