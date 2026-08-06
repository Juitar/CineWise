package com.miaoyu.ticket.order.application;

import com.miaoyu.ticket.common.config.ClockConfiguration;
import com.miaoyu.ticket.common.error.BusinessException;
import com.miaoyu.ticket.common.id.BusinessIdGenerator;
import com.miaoyu.ticket.order.domain.OrderStatus;
import com.miaoyu.ticket.order.domain.PaymentStatus;
import com.miaoyu.ticket.order.event.PaymentSucceededEvent;
import com.miaoyu.ticket.order.event.PaymentSucceededEventPublisher;
import com.miaoyu.ticket.ticketing.application.SeatSaleService;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 在同一本地事务中完成支付、售座、订单迁移和电子票签发。 */
@Service
public class PaymentTransaction {

    private static final Logger LOGGER = LoggerFactory.getLogger(PaymentTransaction.class);
    private static final String PAYMENT_NUMBER_PREFIX = "PAY";
    private static final String TICKET_CODE_PREFIX = "TKT";
    private static final String QR_PAYLOAD_PREFIX = "cinewise:ticket:";

    private final OrderRepository orderRepository;
    private final PaymentRepository paymentRepository;
    private final PaymentViewFactory paymentViewFactory;
    private final SeatSaleService seatSaleService;
    private final BusinessIdGenerator idGenerator;
    private final Clock clock;
    private final PaymentSucceededEventPublisher paymentSucceededEventPublisher;

    public PaymentTransaction(
            OrderRepository orderRepository,
            PaymentRepository paymentRepository,
            PaymentViewFactory paymentViewFactory,
            SeatSaleService seatSaleService,
            BusinessIdGenerator idGenerator,
            Clock clock,
            PaymentSucceededEventPublisher paymentSucceededEventPublisher) {
        this.orderRepository = orderRepository;
        this.paymentRepository = paymentRepository;
        this.paymentViewFactory = paymentViewFactory;
        this.seatSaleService = seatSaleService;
        this.idGenerator = idGenerator;
        this.clock = clock;
        this.paymentSucceededEventPublisher = paymentSucceededEventPublisher;
    }

    /** 锁定本人订单行，使支付、取消和过期对同一订单串行决定终态。 */
    @Transactional
    public PaymentView pay(
            long userId,
            String orderNo,
            String idempotencyKey,
            Optional<TravelEventContextResolver.TravelEventContext> eventContext) {
        OrderRepository.OrderSnapshot order = orderRepository.findByOrderNoForUpdate(userId, orderNo)
                .orElseThrow(() -> new BusinessException(OrderErrorCode.ORDER_NOT_FOUND));
        PaymentRepository.PaymentSnapshot paymentByKey = paymentRepository
                .findPaymentByIdempotencyKey(idempotencyKey)
                .orElse(null);
        if (paymentByKey != null && paymentByKey.orderId() != order.orderId()) {
            throw new BusinessException(OrderErrorCode.IDEMPOTENCY_PARAMETER_MISMATCH);
        }
        PaymentRepository.PaymentSnapshot existingPayment = paymentRepository
                .findPaymentByOrderId(order.orderId())
                .orElse(null);
        if (existingPayment != null) {
            return createExistingResult(order, existingPayment);
        }

        LocalDateTime paidAt = currentBusinessTime();
        validatePayable(order, paidAt);
        long paymentId = idGenerator.nextId();
        paymentRepository.insertProcessingPayment(new PaymentRepository.NewPayment(
                paymentId,
                PAYMENT_NUMBER_PREFIX + paymentId,
                order.orderId(),
                idempotencyKey,
                order.totalAmount(),
                paidAt));
        if (!orderRepository.markOrderPaying(order.orderId(), order.version(), paidAt, paidAt)) {
            throw new BusinessException(OrderErrorCode.ORDER_STATE_CONFLICT);
        }

        int soldSeatCount = seatSaleService.sellLockedSeats(order.orderNo(), paidAt);
        if (soldSeatCount != order.ticketCount()) {
            throw new IllegalStateException("支付订单的座位归属不完整，已回滚");
        }
        if (!paymentRepository.completePayment(paymentId, 0, paidAt)) {
            throw new IllegalStateException("Mock支付状态迁移失败，已回滚");
        }
        if (!orderRepository.markOrderPaid(order.orderId(), order.version() + 1, paidAt)) {
            throw new IllegalStateException("订单支付完成状态迁移失败，已回滚");
        }

        long ticketId = idGenerator.nextId();
        String ticketCode = TICKET_CODE_PREFIX + ticketId;
        paymentRepository.insertTicket(new PaymentRepository.NewTicket(
                ticketId,
                ticketCode,
                order.orderId(),
                userId,
                QR_PAYLOAD_PREFIX + ticketCode,
                paidAt));
        PaymentView paidResult = loadCommittedResult(order.orderId());
        eventContext.ifPresent(context -> publishPaymentSucceededSafely(order, paidResult, context, paidAt));
        return paidResult;
    }

    private PaymentView createExistingResult(
            OrderRepository.OrderSnapshot order,
            PaymentRepository.PaymentSnapshot payment) {
        PaymentRepository.TicketSnapshot ticket = paymentRepository.findTicketByOrderId(order.orderId())
                .orElse(null);
        if (payment.status() == PaymentStatus.SUCCESS && ticket == null) {
            throw new IllegalStateException("成功支付缺少电子票");
        }
        return paymentViewFactory.create(order, payment, ticket);
    }

    private PaymentView loadCommittedResult(long orderId) {
        OrderRepository.OrderSnapshot paidOrder = orderRepository.findById(orderId)
                .orElseThrow(() -> new IllegalStateException("支付后订单丢失"));
        PaymentRepository.PaymentSnapshot successfulPayment = paymentRepository.findPaymentByOrderId(orderId)
                .orElseThrow(() -> new IllegalStateException("支付后记录丢失"));
        PaymentRepository.TicketSnapshot ticket = paymentRepository.findTicketByOrderId(orderId)
                .orElseThrow(() -> new IllegalStateException("支付后电子票丢失"));
        return paymentViewFactory.create(paidOrder, successfulPayment, ticket);
    }

    private void validatePayable(OrderRepository.OrderSnapshot order, LocalDateTime paidAt) {
        if (order.status() != OrderStatus.PENDING_PAYMENT) {
            throw new BusinessException(OrderErrorCode.ORDER_STATE_CONFLICT);
        }
        if (!paidAt.isBefore(order.expireTime())) {
            throw new BusinessException(OrderErrorCode.ORDER_EXPIRED);
        }
        if (order.totalAmount().compareTo(order.unitPrice().multiply(
                java.math.BigDecimal.valueOf(order.ticketCount()))) != 0) {
            throw new IllegalStateException("订单金额快照不一致，拒绝支付");
        }
    }

    /**
     * 事件在事务仍活动时登记，D的事务监听器只会在提交成功后执行。
     * 同步发布器异常被隔离，避免非关键提醒链路把权威支付事务标记为回滚。
     *
     * <p>该方法只在首次创建支付和电子票后调用；已有支付从前置幂等分支返回，所以不会为重放请求
     * 生成新的eventId。若最终数据库提交失败，AFTER_COMMIT消费者也不会执行。</p>
     */
    private void publishPaymentSucceededSafely(
            OrderRepository.OrderSnapshot originalOrder,
            PaymentView paidResult,
            TravelEventContextResolver.TravelEventContext context,
            LocalDateTime paidAt) {
        if (context.showId() != originalOrder.showId()) {
            LOGGER.warn("支付事件场次上下文不匹配, orderId={}", originalOrder.orderId());
            return;
        }
        PaymentSucceededEvent event = new PaymentSucceededEvent(
                UUID.randomUUID().toString(),
                Long.toString(originalOrder.orderId()),
                Long.toString(originalOrder.showId()),
                Long.toString(context.cinemaId()),
                Long.toString(originalOrder.userId()),
                context.cinemaArea(),
                context.startAt().atZone(ClockConfiguration.BUSINESS_ZONE_ID).toOffsetDateTime(),
                paidResult.stateVersion(),
                paidAt.atZone(ClockConfiguration.BUSINESS_ZONE_ID).toOffsetDateTime());
        try {
            paymentSucceededEventPublisher.publish(event);
        } catch (RuntimeException exception) {
            LOGGER.warn(
                    "支付成功事件登记失败, orderId={}, eventId={}, errorType={}",
                    originalOrder.orderId(),
                    event.eventId(),
                    exception.getClass().getSimpleName());
        }
    }

    private LocalDateTime currentBusinessTime() {
        return LocalDateTime.ofInstant(clock.instant(), ClockConfiguration.BUSINESS_ZONE_ID);
    }
}
