package com.miaoyu.ticket.order.infrastructure.persistence;

import java.time.LocalDateTime;

/** 订单写动作幂等关系的不可变写入输入。 */
public record OrderOperationInsertRow(
        long id,
        long userId,
        String action,
        String idempotencyKey,
        long orderId,
        String orderNoSnapshot,
        String parameterHash,
        String resultStatus,
        int resultVersion,
        LocalDateTime createdAt) {
}
