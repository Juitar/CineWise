package com.miaoyu.ticket.order.application;

import com.miaoyu.ticket.common.config.ClockConfiguration;
import com.miaoyu.ticket.common.config.TicketingTransactionProperties;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/** 有界扫描过期候选，并为每个订单启动独立事务。 */
@Service
public class OrderExpiryService {

    private static final Logger LOGGER = LoggerFactory.getLogger(OrderExpiryService.class);

    private final OrderRepository repository;
    private final OrderExpiryTransaction expiryTransaction;
    private final TicketingTransactionProperties properties;
    private final Clock clock;

    public OrderExpiryService(
            OrderRepository repository,
            OrderExpiryTransaction expiryTransaction,
            TicketingTransactionProperties properties,
            Clock clock) {
        this.repository = repository;
        this.expiryTransaction = expiryTransaction;
        this.properties = properties;
        this.clock = clock;
    }

    /** 单条失败只记录内部订单ID，不会回滚同批其他订单或终止后续调度。 */
    public OrderExpiryReport releaseExpiredOrders() {
        LocalDateTime now = LocalDateTime.ofInstant(
                clock.instant(),
                ClockConfiguration.BUSINESS_ZONE_ID);
        List<Long> candidateIds = repository.findExpiredCandidateIds(
                now,
                properties.expiryBatchSize());
        int expiredCount = 0;
        int skippedCount = 0;
        int failedCount = 0;
        for (long orderId : candidateIds) {
            try {
                if (expiryTransaction.expire(orderId, now)) {
                    expiredCount++;
                } else {
                    skippedCount++;
                }
            } catch (RuntimeException exception) {
                failedCount++;
                LOGGER.warn("过期订单释放失败, orderId={}", orderId, exception);
            }
        }
        return new OrderExpiryReport(candidateIds.size(), expiredCount, skippedCount, failedCount);
    }
}
