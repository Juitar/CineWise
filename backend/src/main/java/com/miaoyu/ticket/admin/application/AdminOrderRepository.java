package com.miaoyu.ticket.admin.application;

import com.miaoyu.ticket.order.domain.ElectronicTicketStatus;
import com.miaoyu.ticket.order.domain.OrderStatus;
import com.miaoyu.ticket.order.domain.PaymentStatus;
import com.miaoyu.ticket.order.domain.RefundStatus;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * A拥有的管理订单只读持久化端口。
 *
 * <p>所有方法只读取A的交易与场次表；接口没有新增、更新或删除能力，
 * 也不允许实现通过JOIN访问C的sys_user。</p>
 *
 * <p>列表采用“主分页后批量补关联”的结构，而不是一次连接所有一对多表。
 * 这样可以保证COUNT和分页行数不被座位明细放大，也能让每类关联查询保持有界。</p>
 *
 * <p>端口投影主动移除敏感列。即使上层DTO以后误用整个投影，也无法取得
 * clientRequestId、幂等键、二维码载荷、退款影响快照或Agent actionId。</p>
 */
public interface AdminOrderRepository {

    /** 统计规范化筛选条件命中的订单总数。 */
    long countOrders(Criteria criteria);

    /** 按create_time、id倒序返回稳定分页主行。 */
    List<OrderSnapshot> findOrderPage(Criteria criteria);

    /** 管理详情按全局唯一订单号查询，不附加普通用户归属条件。 */
    Optional<OrderSnapshot> findByOrderNo(String orderNo);

    /** 详情一次加载订单创建时的座位快照；列表不调用。 */
    List<SeatSnapshot> findSeatsByOrderIds(List<Long> orderIds);

    /** 按当前页订单ID一次批量加载支付摘要，避免逐订单查询。 */
    List<PaymentSnapshot> findPaymentsByOrderIds(List<Long> orderIds);

    /** 按当前页订单ID一次批量加载电子票摘要，且不选择qr_payload。 */
    List<TicketSnapshot> findTicketsByOrderIds(List<Long> orderIds);

    /** 按当前页订单ID一次批量加载退款摘要，且不选择内部确认字段。 */
    List<RefundSnapshot> findRefundsByOrderIds(List<Long> orderIds);

    /**
     * Repository只接收已经校验且有界的条件。
     *
     * <p>userIds为null表示不启用用户筛选；非null集合最多100个且不能为空。
     * offset和limit由应用层检查，Mapper不接受客户端传入的排序列。</p>
     */
    record Criteria(
            String orderNo,
            Set<Long> userIds,
            OrderStatus status,
            Long movieId,
            Long showId,
            LocalDateTime createdAtOrAfter,
            LocalDateTime createdBefore,
            int offset,
            int limit) {
    }

    /**
     * 订单和场次引用的最小主投影，不包含写操作幂等字段。
     *
     * <p>场次ID、影片ID和影院ID均来自A的movie_show引用；管理查询不访问D的内容Mapper。
     * createdAt用于稳定排序，updatedAt和version用于识别刷新后的新状态。</p>
     */
    record OrderSnapshot(
            long orderId,
            String orderNo,
            long userId,
            long showId,
            long movieId,
            long cinemaId,
            LocalDateTime showStartTime,
            int ticketCount,
            BigDecimal unitPrice,
            BigDecimal totalAmount,
            OrderStatus status,
            LocalDateTime expireTime,
            LocalDateTime paidTime,
            LocalDateTime cancelledTime,
            LocalDateTime refundedTime,
            int version,
            LocalDateTime createdAt,
            LocalDateTime updatedAt) {
    }

    /**
     * 详情使用订单快照字段，不回查show_seat当前状态覆盖历史座位。
     *
     * <p>退票后当前座位可能已经恢复AVAILABLE，但订单详情仍必须展示当时购买的行列和单价。</p>
     */
    record SeatSnapshot(
            long orderId,
            long seatId,
            String rowNo,
            String seatNo,
            BigDecimal unitPrice) {
    }

    /**
     * 每个订单最多一条支付记录，由数据库唯一约束保证。
     *
     * <p>requestTime与paidTime分别表示开始处理和形成成功终态，不能互相替代。</p>
     */
    record PaymentSnapshot(
            long orderId,
            String paymentNo,
            BigDecimal amount,
            PaymentStatus status,
            LocalDateTime requestedAt,
            LocalDateTime paidAt,
            int version,
            LocalDateTime updatedAt) {
    }

    /**
     * 每个订单最多一张电子票，投影主动排除二维码载荷。
     *
     * <p>invalidatedAt只有退款或作废后存在，不能根据订单状态在查询时临时生成。</p>
     */
    record TicketSnapshot(
            long orderId,
            String ticketCode,
            ElectronicTicketStatus status,
            LocalDateTime issuedAt,
            LocalDateTime invalidatedAt,
            int version,
            LocalDateTime updatedAt) {
    }

    /**
     * 每个订单最多一条退款记录，投影主动排除影响快照和actionId。
     *
     * <p>reason允许为空；processedAt只在退款形成处理结果后出现。</p>
     */
    record RefundSnapshot(
            long orderId,
            String refundNo,
            String reason,
            RefundStatus status,
            LocalDateTime requestedAt,
            LocalDateTime processedAt,
            int version,
            LocalDateTime updatedAt) {
    }
}
