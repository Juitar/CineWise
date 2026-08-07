package com.miaoyu.ticket.order.application;

import com.miaoyu.ticket.auth.application.CurrentUserAccessor;
import com.miaoyu.ticket.common.error.BusinessException;
import com.miaoyu.ticket.common.error.CommonErrorCode;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 只返回当前认证用户的电子票详情。 */
@Service
public class ElectronicTicketQueryService {

    private final CurrentUserAccessor currentUserAccessor;
    private final PaymentRepository paymentRepository;
    private final OrderRepository orderRepository;

    public ElectronicTicketQueryService(
            CurrentUserAccessor currentUserAccessor,
            PaymentRepository paymentRepository,
            OrderRepository orderRepository) {
        this.currentUserAccessor = currentUserAccessor;
        this.paymentRepository = paymentRepository;
        this.orderRepository = orderRepository;
    }

    /** 电子票先按用户过滤，再聚合订单和座位快照。 */
    @Transactional(readOnly = true)
    public ElectronicTicketView queryTicket(long ticketId) {
        if (ticketId <= 0) {
            throw new BusinessException(CommonErrorCode.INVALID_PARAMETER);
        }
        long currentUserId = currentUserAccessor.requireCurrentUserId();
        PaymentRepository.TicketSnapshot ticket = paymentRepository
                .findTicketByIdAndUser(ticketId, currentUserId)
                .orElseThrow(() -> new BusinessException(OrderErrorCode.ORDER_NOT_FOUND));
        OrderRepository.OrderSnapshot order = orderRepository.findById(ticket.orderId())
                .orElseThrow(() -> new IllegalStateException("电子票关联订单丢失"));
        if (order.userId() != currentUserId) {
            throw new IllegalStateException("电子票与订单用户归属不一致");
        }
        return new ElectronicTicketView(
                ticket.ticketId(),
                ticket.ticketCode(),
                order.orderId(),
                order.orderNo(),
                order.showId(),
                orderRepository.findSeatIds(order.orderId()),
                ticket.status(),
                ticket.invalidationReason(),
                ticket.qrPayload(),
                ticket.issuedAt(),
                ticket.version(),
                ticket.updatedAt());
    }
}
