package com.miaoyu.ticket.order.api;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * 固定页面退票请求。
 *
 * <ul>
 *   <li>refundReason是可选说明，不影响服务端退款金额；</li>
 *   <li>clientRequestId由页面确认动作生成并在结果未知时保持稳定；</li>
 *   <li>请求体不包含userId、退款金额、订单状态或票状态；</li>
 *   <li>字段长度在进入应用服务前受Bean Validation限制。</li>
 * </ul>
 * <p>传统页面省略actionId；Agent未来经Tool Adapter校验确认后调用应用层，不直调本REST。</p>
 */
public record CreateRefundRequest(
        @Size(max = 255)
        @Schema(example = "行程变化", nullable = true)
        String refundReason,
        @NotBlank
        @Size(max = 64)
        @Schema(example = "refund-request-01")
        String clientRequestId,
        @Size(max = 64)
        @Schema(nullable = true, description = "仅未来Agent适配层使用；传统页面必须省略")
        String actionId) {
}
