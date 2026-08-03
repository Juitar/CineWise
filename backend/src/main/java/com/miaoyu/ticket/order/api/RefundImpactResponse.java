package com.miaoyu.ticket.order.api;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.OffsetDateTime;

/**
 * 退票前二次确认所需的只读权威摘要。
 *
 * <ul>
 *   <li>金额已经格式化为两位小数字符串；</li>
 *   <li>时间带Asia/Shanghai对应偏移；</li>
 *   <li>版本帮助页面识别展示后发生的状态变化；</li>
 *   <li>响应不包含可被当作B确认凭证的字段。</li>
 * </ul>
 */
public record RefundImpactResponse(
        @Schema(example = "90001") String orderId,
        @Schema(example = "CW90001") String orderNo,
        @Schema(example = "88.00") String refundAmount,
        @Schema(example = "PAID") String orderStatus,
        @Schema(example = "VALID") String ticketStatus,
        OffsetDateTime showStartTime,
        int orderVersion,
        int ticketVersion,
        String impactText) {
}
