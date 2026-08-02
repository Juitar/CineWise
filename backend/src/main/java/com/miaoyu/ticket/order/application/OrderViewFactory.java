package com.miaoyu.ticket.order.application;

import java.util.List;
import org.springframework.stereotype.Component;

/** 将订单权威快照与座位快照组合为应用视图。 */
@Component
public class OrderViewFactory {

    private final OrderRepository repository;

    public OrderViewFactory(OrderRepository repository) {
        this.repository = repository;
    }

    public OrderView create(OrderRepository.OrderSnapshot order) {
        return create(order, repository.findSeatIds(order.orderId()));
    }

    public OrderView create(OrderRepository.OrderSnapshot order, List<Long> seatIds) {
        return new OrderView(
                order.orderId(),
                order.orderNo(),
                order.showId(),
                List.copyOf(seatIds),
                order.ticketCount(),
                order.unitPrice(),
                order.totalAmount(),
                order.status(),
                order.expireTime(),
                order.version(),
                order.updatedAt());
    }
}
