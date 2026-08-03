package com.miaoyu.ticket.order.application;

import org.springframework.stereotype.Component;

/**
 * 将三张权威记录组装为对外退款结果。
 *
 * <ul>
 *   <li>退款金额来自refund_request创建快照；</li>
 *   <li>订单状态来自ticket_order，不从退款状态推断；</li>
 *   <li>票状态来自electronic_ticket，不从订单状态推断；</li>
 *   <li>更新时间取三者最新值，帮助客户端拒绝旧响应。</li>
 * </ul>
 * <p>Controller只做序列化，不能自行拼接或推断交易终态。</p>
 */
@Component
public class RefundViewFactory {

    /**
     * 使用退款记录金额并返回三方最高版本。
     *
     * <p>响应版本只用于拒绝旧页面结果，不能作为下一次状态迁移的数据库条件。</p>
     */
    public RefundView create(
            OrderRepository.OrderSnapshot order,
            RefundRepository.RefundSnapshot refund,
            PaymentRepository.TicketSnapshot ticket) {
        int stateVersion = Math.max(order.version(), Math.max(refund.version(), ticket.version()));
        return new RefundView(
                refund.refundId(),
                refund.refundNo(),
                order.orderId(),
                order.orderNo(),
                refund.status(),
                refund.refundAmount(),
                order.status(),
                ticket.status(),
                stateVersion,
                latest(order.updatedAt(), refund.updatedAt(), ticket.updatedAt()));
    }

    private java.time.LocalDateTime latest(java.time.LocalDateTime... values) {
        // 三张记录在同一事务写入，但数据库毫秒截断和未来兼容迁移可能产生不同更新时间。
        // 显式取最大值比假设固定写入顺序更稳健，也不会改变任何权威状态。
        java.time.LocalDateTime result = values[0];
        for (int index = 1; index < values.length; index++) {
            if (values[index].isAfter(result)) {
                result = values[index];
            }
        }
        return result;
    }
}
