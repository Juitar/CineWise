package com.miaoyu.ticket.order.infrastructure.persistence;

import java.time.LocalDateTime;

/** 电子票持久化投影。 */
public record TicketSnapshotRow(
        long ticketId,
        String ticketCode,
        long orderId,
        long userId,
        String status,
        String qrPayload,
        LocalDateTime issuedAt,
        LocalDateTime invalidatedAt,
        int version,
        LocalDateTime updatedAt) {
}
