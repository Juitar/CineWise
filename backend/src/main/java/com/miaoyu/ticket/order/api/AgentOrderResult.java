package com.miaoyu.ticket.order.api;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.OffsetDateTime;
import java.util.List;

/**
 * Agent 建单工具的安全结果投影。
 *
 * <p>所有业务 ID 和金额按 A/B 跨模块契约使用字符串，避免调用方语言的数值精度损失；该类型不暴露
 * 订单实体、用户信息、确认凭证或数据库内部字段。</p>
 */
public record AgentOrderResult(
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

    public AgentOrderResult {
        seatIds = List.copyOf(seatIds);
    }
}
