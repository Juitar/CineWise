package com.miaoyu.ticket.admin.api;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.time.OffsetDateTime;

/**
 * 管理订单列表的最小脱敏摘要。
 *
 * <p>业务ID保持十进制字符串，金额保持两位小数字符串，避免浏览器精度丢失。
 * emailMasked显式允许null，以表示历史用户信息已经不可用而订单仍然存在。</p>
 *
 * <p>三个关联状态可以为空：待支付订单可能没有支付记录，未支付订单不会有电子票，
 * 未申请退票的订单不会有退款记录。调用方不得把null渲染成失败。</p>
 *
 * <p>列表不包含支付号、票号、退款原因和座位明细，降低批量响应暴露面；这些字段只在
 * 管理员主动打开单笔详情后返回。</p>
 */
public record AdminOrderSummaryResponse(
        String orderId,
        String orderNo,
        String userId,
        @JsonInclude(JsonInclude.Include.ALWAYS) String emailMasked,
        String showId,
        String movieId,
        String cinemaId,
        OffsetDateTime showStartTime,
        int ticketCount,
        String unitPrice,
        String totalAmount,
        String orderStatus,
        OffsetDateTime expireTime,
        String paymentStatus,
        String ticketStatus,
        String refundStatus,
        int stateVersion,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt) {
}
