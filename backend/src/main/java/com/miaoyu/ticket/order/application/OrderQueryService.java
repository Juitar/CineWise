package com.miaoyu.ticket.order.application;

import com.miaoyu.ticket.auth.application.CurrentUserAccessor;
import com.miaoyu.ticket.common.config.ApiProperties;
import com.miaoyu.ticket.common.error.BusinessException;
import com.miaoyu.ticket.common.error.CommonErrorCode;
import com.miaoyu.ticket.order.domain.OrderStatus;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 只返回当前认证用户订单的分页列表与详情。 */
@Service
public class OrderQueryService {

    private static final int MAXIMUM_ORDER_NUMBER_LENGTH = 32;

    private final OrderRepository repository;
    private final CurrentUserAccessor currentUserAccessor;
    private final ApiProperties apiProperties;

    public OrderQueryService(
            OrderRepository repository,
            CurrentUserAccessor currentUserAccessor,
            ApiProperties apiProperties) {
        this.repository = repository;
        this.currentUserAccessor = currentUserAccessor;
        this.apiProperties = apiProperties;
    }

    /** 分页查订单后一次批量加载本页座位，不对每行执行N+1查询。 */
    @Transactional(readOnly = true)
    public OrderPageView queryOrders(OrderListQuery query) {
        long currentUserId = currentUserAccessor.requireCurrentUserId();
        OrderRepository.OrderListCriteria criteria = toCriteria(currentUserId, query);
        long total = repository.countOrders(criteria);
        if (total == 0) {
            return new OrderPageView(0, resolvePage(query), resolveSize(query), List.of());
        }
        List<OrderRepository.OrderQuerySnapshot> orders = repository.findOrderQueryPage(criteria);
        Map<Long, List<Long>> seatIdsByOrder = groupSeatIds(orders);
        List<OrderQueryView> records = orders.stream()
                .map(order -> toView(
                        order,
                        seatIdsByOrder.getOrDefault(order.orderId(), List.of())))
                .toList();
        return new OrderPageView(total, resolvePage(query), resolveSize(query), records);
    }

    /** 先按当前用户过滤再查订单号，跨用户查询与不存在统一返回404。 */
    @Transactional(readOnly = true)
    public OrderQueryView queryOrder(String orderNo) {
        validateOrderNo(orderNo);
        long currentUserId = currentUserAccessor.requireCurrentUserId();
        OrderRepository.OrderQuerySnapshot order = repository.findOrderQueryByOrderNo(currentUserId, orderNo)
                .orElseThrow(() -> new BusinessException(OrderErrorCode.ORDER_NOT_FOUND));
        return toView(order, repository.findSeatIds(order.orderId()));
    }

    private OrderRepository.OrderListCriteria toCriteria(long userId, OrderListQuery query) {
        if (query == null) {
            throw new BusinessException(CommonErrorCode.INVALID_PARAMETER);
        }
        String orderNo = normalizeOptionalOrderNo(query.orderNo());
        OrderStatus status = parseStatus(query.status());
        if (query.dateFrom() != null
                && query.dateTo() != null
                && query.dateFrom().isAfter(query.dateTo())) {
            throw new BusinessException(CommonErrorCode.INVALID_PARAMETER, "dateFrom不得晚于dateTo");
        }
        int page = resolvePage(query);
        int size = resolveSize(query);
        long offset = (long) (page - 1) * size;
        if (page < 1 || size < 1 || size > apiProperties.maxPageSize() || offset > Integer.MAX_VALUE) {
            throw new BusinessException(CommonErrorCode.INVALID_PARAMETER);
        }
        LocalDateTime createdAtOrAfter = query.dateFrom() == null
                ? null
                : query.dateFrom().atStartOfDay();
        LocalDateTime createdBefore = query.dateTo() == null
                ? null
                : query.dateTo().plusDays(1).atStartOfDay();
        return new OrderRepository.OrderListCriteria(
                userId,
                orderNo,
                status,
                createdAtOrAfter,
                createdBefore,
                (int) offset,
                size);
    }

    private Map<Long, List<Long>> groupSeatIds(List<OrderRepository.OrderQuerySnapshot> orders) {
        List<Long> orderIds = orders.stream()
                .map(OrderRepository.OrderQuerySnapshot::orderId)
                .toList();
        Map<Long, List<Long>> grouped = new HashMap<>();
        for (OrderRepository.OrderSeatReference seat : repository.findSeatIdsByOrderIds(orderIds)) {
            grouped.computeIfAbsent(seat.orderId(), ignored -> new ArrayList<>())
                    .add(seat.seatId());
        }
        return grouped;
    }

    /** 只组装查询字段，不把展示投影转换成可用于交易状态迁移的OrderSnapshot。 */
    private OrderQueryView toView(
            OrderRepository.OrderQuerySnapshot order,
            List<Long> seatIds) {
        return new OrderQueryView(
                order.orderId(),
                order.orderNo(),
                order.showId(),
                order.movieId(),
                order.cinemaId(),
                order.showStartTime(),
                seatIds,
                order.ticketCount(),
                order.unitPrice(),
                order.totalAmount(),
                order.status(),
                order.expireTime(),
                order.version(),
                order.updatedAt());
    }

    private String normalizeOptionalOrderNo(String orderNo) {
        if (orderNo == null || orderNo.isBlank()) {
            return null;
        }
        validateOrderNo(orderNo);
        return orderNo;
    }

    private void validateOrderNo(String orderNo) {
        if (orderNo == null || orderNo.isBlank() || orderNo.length() > MAXIMUM_ORDER_NUMBER_LENGTH) {
            throw new BusinessException(CommonErrorCode.INVALID_PARAMETER);
        }
    }

    private OrderStatus parseStatus(String status) {
        if (status == null || status.isBlank()) {
            return null;
        }
        try {
            return OrderStatus.valueOf(status);
        } catch (IllegalArgumentException exception) {
            throw new BusinessException(CommonErrorCode.INVALID_PARAMETER, "订单状态不合法");
        }
    }

    private int resolvePage(OrderListQuery query) {
        return query.page() == null ? apiProperties.defaultPage() : query.page();
    }

    private int resolveSize(OrderListQuery query) {
        return query.size() == null ? apiProperties.defaultPageSize() : query.size();
    }
}
