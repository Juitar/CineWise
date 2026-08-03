package com.miaoyu.ticket.order.application;

import com.miaoyu.ticket.order.domain.PaymentStatus;
import org.springframework.stereotype.Component;

/** 聚合订单、支付和可选电子票为支付恢复结果。 */
@Component
public class PaymentViewFactory {

    /** SUCCESS支付必须同时存在唯一电子票，避免查询层掩盖部分交易状态。 */
    public PaymentView create(
            OrderRepository.OrderSnapshot order,
            PaymentRepository.PaymentSnapshot payment,
            PaymentRepository.TicketSnapshot ticket) {
        if (payment.status() == PaymentStatus.SUCCESS && ticket == null) {
            throw new IllegalStateException("成功支付缺少电子票");
        }
        Long ticketId = ticket == null ? null : ticket.ticketId();
        return new PaymentView(
                order.orderId(),
                order.orderNo(),
                payment.paymentNo(),
                order.status(),
                payment.status(),
                ticketId,
                order.version(),
                order.updatedAt());
    }
}
