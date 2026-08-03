package com.miaoyu.ticket.order.application;

import com.miaoyu.ticket.order.domain.RefundStatus;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Optional;

/**
 * 退款权威持久化端口。
 *
 * <ul>
 *   <li>仓储不提供任意状态更新，只暴露REQUESTED、PROCESSING和SUCCESS具名迁移；</li>
 *   <li>每订单唯一约束防止一张电子票产生第二笔退款；</li>
 *   <li>用户与幂等键唯一约束防止同一用户把请求键绑定多个订单；</li>
 *   <li>impact_snapshot保存创建时参数，后续订单变化不能改写历史请求语义；</li>
 *   <li>所有更新返回影响行数，事务服务必须验证恰好更新一行。</li>
 * </ul>
 * <p>数据库约束是并发最终防线，应用层仍需在订单行锁事务内比较请求快照。</p>
 */
public interface RefundRepository {

    /** 按订单查询原退款，用于重复请求和响应丢失恢复。 */
    Optional<RefundSnapshot> findByOrderId(long orderId);

    /** 幂等键查询限定当前用户，避免不同用户之间错误复用请求语义。 */
    Optional<RefundSnapshot> findByUserAndIdempotencyKey(long userId, String idempotencyKey);

    /** 首次写入REQUESTED及确定性影响快照。 */
    void insertRequestedRefund(NewRefund refund);

    /** 只有REQUESTED退款可以进入PROCESSING。 */
    boolean markProcessing(long refundId, int expectedVersion, LocalDateTime updatedAt);

    /** 只有PROCESSING退款可以固定成功。 */
    boolean markSuccess(long refundId, int expectedVersion, LocalDateTime processedAt);

    record RefundSnapshot(
            long refundId,
            String refundNo,
            long orderId,
            long userId,
            String idempotencyKey,
            String clientRequestId,
            String refundReason,
            String actionId,
            BigDecimal refundAmount,
            int orderVersionAtRequest,
            int ticketVersionAtRequest,
            RefundStatus status,
            LocalDateTime requestTime,
            LocalDateTime processedTime,
            int version,
            LocalDateTime updatedAt) {
    }

    /**
     * 首次退款写入的完整权威输入。
     *
     * <p>金额、订单版本、票版本和开场时间均由服务端重读；请求方只能提供原因和稳定请求标识。</p>
     */
    record NewRefund(
            long refundId,
            String refundNo,
            long orderId,
            long userId,
            String idempotencyKey,
            String clientRequestId,
            String refundReason,
            String actionId,
            BigDecimal refundAmount,
            int orderVersionAtRequest,
            int ticketVersionAtRequest,
            LocalDateTime showStartTime,
            LocalDateTime requestedAt) {
    }
}
