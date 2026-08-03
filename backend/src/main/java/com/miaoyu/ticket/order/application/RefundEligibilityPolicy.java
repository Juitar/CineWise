package com.miaoyu.ticket.order.application;

import com.miaoyu.ticket.common.error.BusinessException;
import com.miaoyu.ticket.order.domain.ElectronicTicketStatus;
import com.miaoyu.ticket.order.domain.OrderStatus;
import com.miaoyu.ticket.order.domain.PaymentStatus;
import com.miaoyu.ticket.ticketing.application.RefundShowRepository;
import java.time.LocalDateTime;
import org.springframework.stereotype.Component;

/**
 * 退票资格的单一业务规则入口。
 *
 * <ul>
 *   <li>订单必须仍为PAID，不能从CANCELLED或REFUNDED重新打开；</li>
 *   <li>支付必须为SUCCESS且金额等于订单权威总额；</li>
 *   <li>电子票必须仍为VALID，作废票和已退票不可再次退款；</li>
 *   <li>原场次必须严格晚于当前业务时间；</li>
 *   <li>订单快照中的全部座位必须仍为SOLD。</li>
 * </ul>
 * <p>影响查询和写事务共用本策略，避免页面先显示可退而事务采用另一套金额或截止规则。</p>
 */
@Component
public class RefundEligibilityPolicy {

    /**
     * 校验订单、支付、票、场次和座位五类权威事实。
     *
     * <p>开场时间等于当前业务时间时视为已到截止边界；退款金额必须与成功支付一致。</p>
     */
    public void requireRefundable(
            OrderRepository.OrderSnapshot order,
            PaymentRepository.PaymentSnapshot payment,
            PaymentRepository.TicketSnapshot ticket,
            RefundShowRepository.RefundShowContext show,
            int soldSeatCount,
            LocalDateTime now) {
        boolean paidOrder = order.status() == OrderStatus.PAID;
        boolean successfulPayment = payment.status() == PaymentStatus.SUCCESS
                && payment.amount().compareTo(order.totalAmount()) == 0;
        boolean validTicket = ticket.status() == ElectronicTicketStatus.VALID;
        boolean showNotStarted = show.startTime().isAfter(now);
        boolean completeSoldSeats = soldSeatCount == order.ticketCount();
        // 任一事实不满足都返回统一不可退语义；不存在部分退款或按缺失座位减少金额的路径。
        // 写事务会在插入退款前再次调用本策略，页面影响摘要不能授权后续写入。
        if (!paidOrder || !successfulPayment || !validTicket || !showNotStarted || !completeSoldSeats) {
            throw new BusinessException(OrderErrorCode.ORDER_NOT_REFUNDABLE);
        }
    }
}
