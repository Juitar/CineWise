package com.miaoyu.ticket.order.application;

import com.miaoyu.ticket.order.domain.ElectronicTicketStatus;
import com.miaoyu.ticket.order.domain.ElectronicTicketInvalidationReason;
import java.time.LocalDateTime;
import java.util.List;

/** 本人电子票及其订单、场次和座位权威引用。 */
public record ElectronicTicketView(
        long ticketId,
        String ticketCode,
        long orderId,
        String orderNo,
        long showId,
        List<Long> seatIds,
        ElectronicTicketStatus status,
        ElectronicTicketInvalidationReason invalidationReason,
        String qrPayload,
        LocalDateTime issuedAt,
        int stateVersion,
        LocalDateTime updatedAt) {

    public ElectronicTicketView {
        seatIds = List.copyOf(seatIds);
    }
}
