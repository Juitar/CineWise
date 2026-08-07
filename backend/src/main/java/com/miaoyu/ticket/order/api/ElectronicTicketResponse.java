package com.miaoyu.ticket.order.api;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.OffsetDateTime;
import java.util.List;

/** 本人电子票详情响应，不暴露支付内部字段。 */
public record ElectronicTicketResponse(
        @Schema(example = "92001") String ticketId,
        @Schema(example = "TKT92001") String ticketCode,
        @Schema(example = "90001") String orderId,
        @Schema(example = "CW90001") String orderNo,
        @Schema(example = "70001") String showId,
        List<String> seatIds,
        @Schema(example = "VALID") String status,
        @Schema(example = "SHOW_ENDED", nullable = true) String invalidationReason,
        @Schema(example = "cinewise:ticket:TKT92001") String qrPayload,
        OffsetDateTime issuedAt,
        int stateVersion,
        OffsetDateTime updatedAt) {

    public ElectronicTicketResponse {
        seatIds = List.copyOf(seatIds);
    }
}
