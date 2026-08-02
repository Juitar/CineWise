package com.miaoyu.ticket.order.application;

import com.miaoyu.ticket.common.config.ClockConfiguration;
import com.miaoyu.ticket.common.config.TicketingTransactionProperties;
import com.miaoyu.ticket.common.id.BusinessIdGenerator;
import com.miaoyu.ticket.order.domain.OrderStatus;
import com.miaoyu.ticket.ticketing.application.SeatLockResult;
import com.miaoyu.ticket.ticketing.application.SeatLockService;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDateTime;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 一次建单的最小本地事务，不包含缓存、HTTP或事务后副作用。 */
@Service
public class OrderCreationTransaction {

    private static final String ORDER_NUMBER_PREFIX = "CW";

    private final OrderRepository orderRepository;
    private final OrderIdempotencyService idempotencyService;
    private final SeatLockService seatLockService;
    private final BusinessIdGenerator idGenerator;
    private final TicketingTransactionProperties properties;
    private final Clock clock;

    public OrderCreationTransaction(
            OrderRepository orderRepository,
            OrderIdempotencyService idempotencyService,
            SeatLockService seatLockService,
            BusinessIdGenerator idGenerator,
            TicketingTransactionProperties properties,
            Clock clock) {
        this.orderRepository = orderRepository;
        this.idempotencyService = idempotencyService;
        this.seatLockService = seatLockService;
        this.idGenerator = idGenerator;
        this.properties = properties;
        this.clock = clock;
    }

    /**
     * 锁座、订单与明细在同一事务内落库。
     * 任一条座位条件更新失败会以运行时异常回滚整笔交易。
     */
    @Transactional
    public OrderView create(long userId, CreateOrderCommand command) {
        return idempotencyService.findMatchingOrder(userId, command)
                .orElseGet(() -> createNewOrder(userId, command));
    }

    private OrderView createNewOrder(long userId, CreateOrderCommand command) {
        long orderId = idGenerator.nextId();
        String orderNo = ORDER_NUMBER_PREFIX + orderId;
        LocalDateTime createdAt = LocalDateTime.ofInstant(
                clock.instant(),
                ClockConfiguration.BUSINESS_ZONE_ID);
        int effectiveHoldMinutes = Math.min(
                properties.ticketLockMinutes(),
                properties.orderPaymentMinutes());
        LocalDateTime expireTime = createdAt.plusMinutes(effectiveHoldMinutes);

        SeatLockResult lockedSeats = seatLockService.lockSeats(
                command.showId(),
                command.seatIds(),
                orderNo,
                expireTime,
                createdAt);
        int ticketCount = lockedSeats.seats().size();
        BigDecimal totalAmount = lockedSeats.unitPrice().multiply(BigDecimal.valueOf(ticketCount));
        orderRepository.insertOrder(new OrderRepository.NewOrder(
                orderId,
                orderNo,
                userId,
                lockedSeats.showId(),
                ticketCount,
                lockedSeats.unitPrice(),
                totalAmount,
                OrderStatus.PENDING_PAYMENT,
                expireTime,
                command.clientRequestId(),
                command.idempotencyKey(),
                createdAt));
        for (SeatLockResult.LockedSeat seat : lockedSeats.seats()) {
            orderRepository.insertOrderSeat(new OrderRepository.NewOrderSeat(
                    idGenerator.nextId(),
                    orderId,
                    seat.seatId(),
                    seat.rowNo(),
                    seat.seatNo(),
                    lockedSeats.unitPrice(),
                    createdAt));
        }
        return new OrderView(
                orderId,
                orderNo,
                lockedSeats.showId(),
                command.seatIds(),
                ticketCount,
                lockedSeats.unitPrice(),
                totalAmount,
                OrderStatus.PENDING_PAYMENT,
                expireTime,
                0,
                createdAt);
    }
}
