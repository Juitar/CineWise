package com.miaoyu.ticket.order.api;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.OffsetDateTime;
import java.util.List;

/** 个人订单列表与详情的REST契约，包含A权威场次上下文。 */
public record OrderQueryResponse(
        @Schema(example = "90001") String orderId,
        @Schema(example = "CW90001") String orderNo,
        @Schema(example = "70001") String showId,
        @Schema(example = "50001") String movieId,
        @Schema(example = "60001") String cinemaId,
        @Schema(example = "2026-08-10T14:30:00+08:00") OffsetDateTime showStartTime,
        List<String> seatIds,
        int ticketCount,
        @Schema(example = "39.00") String unitPrice,
        @Schema(example = "78.00") String totalAmount,
        String status,
        OffsetDateTime expireTime,
        int stateVersion,
        OffsetDateTime updatedAt) {
}
