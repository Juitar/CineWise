package com.miaoyu.ticket.order.application;

import com.miaoyu.ticket.auth.application.CurrentUserAccessor;
import com.miaoyu.ticket.common.config.ClockConfiguration;
import com.miaoyu.ticket.common.error.BusinessException;
import com.miaoyu.ticket.common.error.CommonErrorCode;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.util.regex.Pattern;
import org.springframework.dao.DataAccessException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 将 A 的订单投影转换为 D 可依赖的最小摘要。
 *
 * <p>身份在应用层取得，Repository 只接收已解析的正数主键，避免调用方伪造用户范围。</p>
 */
@Service
public class TravelOrderSummaryQueryService implements TravelOrderSummaryQueryPort {

    private static final Pattern POSITIVE_DECIMAL_ID = Pattern.compile("[1-9][0-9]*");

    private final OrderRepository repository;
    private final CurrentUserAccessor currentUserAccessor;

    public TravelOrderSummaryQueryService(
            OrderRepository repository,
            CurrentUserAccessor currentUserAccessor) {
        this.repository = repository;
        this.currentUserAccessor = currentUserAccessor;
    }

    @Override
    @Transactional(readOnly = true)
    public TravelOrderSummary queryMyOrder(String orderId) {
        long parsedOrderId = parseOrderId(orderId);
        long currentUserId = currentUserAccessor.requireCurrentUserId();
        try {
            return repository.findTravelOrderSummaryByIdAndUserId(parsedOrderId, currentUserId)
                    .map(this::toSummary)
                    .orElseThrow(() -> new BusinessException(OrderErrorCode.ORDER_NOT_FOUND));
        } catch (DataAccessException exception) {
            // 数据库故障不能伪装成“订单不存在”，让 D 保留可重试的 503 语义。
            throw new BusinessException(OrderErrorCode.ORDER_QUERY_UNAVAILABLE);
        }
    }

    private long parseOrderId(String orderId) {
        if (orderId == null || !POSITIVE_DECIMAL_ID.matcher(orderId).matches()) {
            throw new BusinessException(CommonErrorCode.INVALID_PARAMETER);
        }
        try {
            return Long.parseLong(orderId);
        } catch (NumberFormatException exception) {
            throw new BusinessException(CommonErrorCode.INVALID_PARAMETER);
        }
    }

    private TravelOrderSummary toSummary(OrderRepository.TravelOrderSummarySnapshot snapshot) {
        if (snapshot.orderId() <= 0
                || snapshot.showId() <= 0
                || snapshot.movieId() <= 0
                || snapshot.cinemaId() <= 0
                || snapshot.orderNo() == null
                || snapshot.showStartTime() == null) {
            throw new BusinessException(OrderErrorCode.ORDER_QUERY_UNAVAILABLE);
        }
        OffsetDateTime startAt = toBusinessOffset(snapshot.showStartTime());
        return new TravelOrderSummary(
                Long.toString(snapshot.orderId()),
                snapshot.orderNo(),
                Long.toString(snapshot.showId()),
                Long.toString(snapshot.movieId()),
                Long.toString(snapshot.cinemaId()),
                startAt);
    }

    private OffsetDateTime toBusinessOffset(LocalDateTime value) {
        return value.atZone(ClockConfiguration.BUSINESS_ZONE_ID).toOffsetDateTime();
    }
}
