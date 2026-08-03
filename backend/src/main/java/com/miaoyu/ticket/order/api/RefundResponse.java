package com.miaoyu.ticket.order.api;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.OffsetDateTime;

/**
 * 退票写入与响应丢失恢复使用的统一权威结果。
 *
 * <ul>
 *   <li>写接口和查询接口返回同一结构，客户端无需猜测结果来源；</li>
 *   <li>全部业务ID输出十进制字符串；</li>
 *   <li>三类状态分别来自退款、订单和电子票权威记录；</li>
 *   <li>不返回内部JSON快照、幂等键或用户身份。</li>
 * </ul>
 */
public record RefundResponse(
        @Schema(example = "93001") String refundId,
        @Schema(example = "RFD93001") String refundNo,
        @Schema(example = "90001") String orderId,
        @Schema(example = "CW90001") String orderNo,
        @Schema(example = "SUCCESS") String refundStatus,
        @Schema(example = "88.00") String refundAmount,
        @Schema(example = "REFUNDED") String orderStatus,
        @Schema(example = "REFUNDED") String ticketStatus,
        int stateVersion,
        OffsetDateTime updatedAt) {
}
