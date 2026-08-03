package com.miaoyu.ticket.order.infrastructure.persistence;

import java.time.LocalDateTime;

/** 支付成功后签发电子票的持久化参数。 */
public record TicketInsertRow(
        long ticketId,
        String ticketCode,
        long orderId,
        long userId,
        String qrPayload,
        LocalDateTime issuedAt) {
}
