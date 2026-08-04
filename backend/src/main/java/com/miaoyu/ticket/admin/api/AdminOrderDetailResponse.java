package com.miaoyu.ticket.admin.api;

import java.time.OffsetDateTime;
import java.util.List;

/**
 * 管理订单详情的只读聚合响应。
 *
 * <p>summary复用列表字段，seats和三个可空交易摘要用于排障展示。
 * 响应不含二维码载荷、幂等键、退款影响快照或Agent确认标识。</p>
 *
 * <p>paidTime、cancelledTime和refundedTime来自订单主表，只在对应迁移实际发生后存在。
 * 页面不能根据状态自行填充当前时间，也不能把缺失时间解释成写操作失败。</p>
 *
 * <p>详情仍然是查询时快照；交易可能在响应后继续变化，管理员刷新时以新的stateVersion
 * 和updatedAt为准，不能通过本响应提交乐观锁更新。</p>
 */
public record AdminOrderDetailResponse(
        AdminOrderSummaryResponse summary,
        OffsetDateTime paidTime,
        OffsetDateTime cancelledTime,
        OffsetDateTime refundedTime,
        List<AdminSeatResponse> seats,
        AdminPaymentResponse payment,
        AdminTicketResponse ticket,
        AdminRefundResponse refund) {

    public AdminOrderDetailResponse {
        // 固化详情快照，避免序列化过程中被调用方修改座位顺序。
        seats = List.copyOf(seats);
    }

    /**
     * 订单创建时保存的座位快照，不暴露锁座归属或当前库存版本。
     *
     * <p>seatId保持字符串，行列号保持原始展示文本，单价固定为两位小数。</p>
     */
    public record AdminSeatResponse(
            String seatId,
            String rowNo,
            String seatNo,
            String unitPrice) {
    }

    /**
     * 支付摘要仅用于只读排障，不包含支付幂等键。
     *
     * <p>paidAt为空表示尚未形成SUCCESS，不由Controller推导失败状态。</p>
     */
    public record AdminPaymentResponse(
            String paymentNo,
            String amount,
            String status,
            OffsetDateTime requestedAt,
            OffsetDateTime paidAt,
            int stateVersion,
            OffsetDateTime updatedAt) {
    }

    /**
     * 电子票摘要不返回qrPayload，管理员不能据此复制可用票码。
     *
     * <p>invalidatedAt为空对VALID票是正常状态，前端按status展示。</p>
     */
    public record AdminTicketResponse(
            String ticketCode,
            String status,
            OffsetDateTime issuedAt,
            OffsetDateTime invalidatedAt,
            int stateVersion,
            OffsetDateTime updatedAt) {
    }

    /**
     * 退款摘要排除内部影响快照、actionId和幂等键。
     *
     * <p>processedAt为空时仍保留REQUESTED或PROCESSING记录，不能从列表中删除。</p>
     */
    public record AdminRefundResponse(
            String refundNo,
            String reason,
            String status,
            OffsetDateTime requestedAt,
            OffsetDateTime processedAt,
            int stateVersion,
            OffsetDateTime updatedAt) {
    }
}
