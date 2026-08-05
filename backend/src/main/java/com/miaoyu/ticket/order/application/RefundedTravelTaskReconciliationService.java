package com.miaoyu.ticket.order.application;

import com.miaoyu.ticket.common.config.ClockConfiguration;
import com.miaoyu.ticket.common.config.RefundedTravelReconciliationProperties;
import com.miaoyu.ticket.order.domain.OrderStatus;
import com.miaoyu.ticket.order.event.OrderInvalidated;
import com.miaoyu.ticket.travel.application.TravelTaskApplicationService;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * 从A的REFUNDED权威订单恢复可能遗漏的D出行任务取消动作。
 *
 * <ul>
 *   <li>每轮冻结二十四小时窗口，以refundedTime和orderId稳定分页；</li>
 *   <li>跨模块调用前重读订单，拒绝状态或版本已经变化的候选；</li>
 *   <li>只通过公共解析器和D Application Service组装、投递失效事件；</li>
 *   <li>单条失败不终止后续候选，下轮可再次幂等补偿。</li>
 * </ul>
 *
 * <p>服务不修改A交易状态、不读取D任务表，也不保存第二份取消完成标记。</p>
 */
@Service
public class RefundedTravelTaskReconciliationService {

    private static final String REFUNDED_INVALID_REASON = "REFUNDED";
    private static final Logger LOGGER = LoggerFactory.getLogger(RefundedTravelTaskReconciliationService.class);

    private final OrderRepository orderRepository;
    private final TravelEventContextResolver travelEventContextResolver;
    private final TravelTaskApplicationService travelTaskApplicationService;
    private final RefundedTravelReconciliationProperties properties;
    private final Clock clock;

    /**
     * 依赖按所有权边界注入：A提供订单与场次事实，D公开服务负责最终取消幂等。
     * Clock和有界配置让扫描窗口、分页和测试结果保持确定。
     */
    public RefundedTravelTaskReconciliationService(
            OrderRepository orderRepository,
            TravelEventContextResolver travelEventContextResolver,
            TravelTaskApplicationService travelTaskApplicationService,
            RefundedTravelReconciliationProperties properties,
            Clock clock) {
        this.orderRepository = orderRepository;
        this.travelEventContextResolver = travelEventContextResolver;
        this.travelTaskApplicationService = travelTaskApplicationService;
        this.properties = properties;
        this.clock = clock;
    }

    /**
     * 任务开始时冻结窗口，新退款留给实时事件或下一轮；每页处理后才推进游标，
     * 因此单条失败不会让同页后续订单丢失。
     */
    public RefundedTravelTaskReconciliationReport reconcileRefundedOrders() {
        LocalDateTime windowEnd = currentBusinessTime();
        LocalDateTime windowStart = windowEnd.minusHours(properties.windowHours());
        ReconciliationCursor cursor = new ReconciliationCursor(windowStart, 0L);
        MutableReport report = new MutableReport();

        while (true) {
            List<OrderRepository.RefundedTravelReconciliationCandidate> candidates =
                    orderRepository.findRefundedTravelReconciliationCandidates(
                            windowStart,
                            windowEnd,
                            cursor.refundedAt(),
                            cursor.orderId(),
                            properties.batchSize());
            if (candidates.isEmpty()) {
                break;
            }

            report.startBatch(candidates.size());
            for (OrderRepository.RefundedTravelReconciliationCandidate candidate : candidates) {
                reconcileCandidate(candidate, report);
            }
            cursor = cursor.advanceTo(candidates.getLast());
            if (candidates.size() < properties.batchSize()) {
                break;
            }
        }
        return report.toReport();
    }

    /**
     * 状态变化和上下文缺失属于可预期跳过；D或基础设施异常计为失败。
     * 捕获范围限制在单条候选，保证同批与后续批次仍有恢复机会。
     */
    private void reconcileCandidate(
            OrderRepository.RefundedTravelReconciliationCandidate candidate,
            MutableReport report) {
        try {
            OrderRepository.OrderSnapshot order = orderRepository.findById(candidate.orderId()).orElse(null);
            if (!isSameRefundedVersion(order, candidate)) {
                report.skip();
                return;
            }

            Optional<TravelEventContextResolver.TravelEventContext> context =
                    travelEventContextResolver.resolve(order);
            if (context.isEmpty() || context.get().showId() != order.showId()) {
                report.skip();
                return;
            }

            travelTaskApplicationService.ensureTaskCancelled(
                    toInvalidationEvent(order, candidate.refundedAt(), context.get()));
            report.ensure();
        } catch (RuntimeException exception) {
            report.fail();
            LOGGER.warn(
                    "REFUNDED出行任务取消补偿失败, orderId={}, errorType={}",
                    candidate.orderId(),
                    exception.getClass().getSimpleName(),
                    exception);
        }
    }

    /** 状态和版本同时一致，才可把扫描快照重新表达为当前退款完成事实。 */
    private boolean isSameRefundedVersion(
            OrderRepository.OrderSnapshot order,
            OrderRepository.RefundedTravelReconciliationCandidate candidate) {
        return order != null
                && order.status() == OrderStatus.REFUNDED
                && order.version() == candidate.orderVersion();
    }

    /**
     * occurredAt保留原退款完成时间；eventId只标识本次补偿投递。
     * 事件字段与实时退款事件一致，D可用同一版本规则处理乱序与重复调用。
     */
    private OrderInvalidated toInvalidationEvent(
            OrderRepository.OrderSnapshot order,
            LocalDateTime refundedAt,
            TravelEventContextResolver.TravelEventContext context) {
        return new OrderInvalidated(
                UUID.randomUUID().toString(),
                Long.toString(order.orderId()),
                Long.toString(order.showId()),
                Long.toString(order.userId()),
                context.cinemaArea(),
                context.startAt().atZone(ClockConfiguration.BUSINESS_ZONE_ID).toOffsetDateTime(),
                order.version(),
                refundedAt.atZone(ClockConfiguration.BUSINESS_ZONE_ID).toOffsetDateTime(),
                REFUNDED_INVALID_REASON);
    }

    private LocalDateTime currentBusinessTime() {
        return LocalDateTime.ofInstant(clock.instant(), ClockConfiguration.BUSINESS_ZONE_ID);
    }

    /** 同毫秒退款必须以orderId作为第二游标，且每次翻页必须严格前进。 */
    private record ReconciliationCursor(LocalDateTime refundedAt, long orderId) {

        private ReconciliationCursor advanceTo(
                OrderRepository.RefundedTravelReconciliationCandidate candidate) {
            boolean advanced = candidate.refundedAt().isAfter(refundedAt)
                    || (candidate.refundedAt().isEqual(refundedAt) && candidate.orderId() > orderId);
            if (!advanced) {
                throw new IllegalStateException("REFUNDED出行补偿游标没有严格前进");
            }
            return new ReconciliationCursor(candidate.refundedAt(), candidate.orderId());
        }
    }

    /** 可变计数仅存在单次调用栈，最终返回不可变且无敏感信息的报告。 */
    private static final class MutableReport {

        private int batchCount;
        private int scannedCount;
        private int ensuredCount;
        private int skippedCount;
        private int failedCount;

        private void startBatch(int batchSize) {
            batchCount++;
            scannedCount += batchSize;
        }

        private void ensure() {
            ensuredCount++;
        }

        private void skip() {
            skippedCount++;
        }

        private void fail() {
            failedCount++;
        }

        private RefundedTravelTaskReconciliationReport toReport() {
            return new RefundedTravelTaskReconciliationReport(
                    batchCount,
                    scannedCount,
                    ensuredCount,
                    skippedCount,
                    failedCount);
        }
    }
}
