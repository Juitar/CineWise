package com.miaoyu.ticket.order.api;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.OffsetDateTime;
import java.util.List;

/** 建单与恢复查询共用的REST订单契约。 */
public record OrderResponse(
        @Schema(example = "90001") String orderId,
        @Schema(example = "CW90001") String orderNo,
        @Schema(example = "70001") String showId,
        List<String> seatIds,
        int ticketCount,
        @Schema(example = "39.00") String unitPrice,
        @Schema(example = "78.00") String totalAmount,
        String status,
        OffsetDateTime expireTime,
        int stateVersion,
        OffsetDateTime updatedAt) {
}
