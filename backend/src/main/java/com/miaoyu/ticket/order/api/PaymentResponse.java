package com.miaoyu.ticket.order.api;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.OffsetDateTime;

/** Mock支付写入和结果恢复响应。 */
public record PaymentResponse(
        @Schema(example = "90001") String orderId,
        @Schema(example = "CW90001") String orderNo,
        @Schema(example = "PAY91001") String paymentNo,
        @Schema(example = "PAID") String orderStatus,
        @Schema(example = "SUCCESS") String paymentStatus,
        @Schema(example = "92001", nullable = true) String ticketId,
        int stateVersion,
        OffsetDateTime updatedAt) {
}
