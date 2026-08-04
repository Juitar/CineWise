package com.miaoyu.ticket.order.application;

import com.miaoyu.ticket.common.config.ClockConfiguration;
import com.miaoyu.ticket.common.config.PaidTravelReconciliationProperties;
import com.miaoyu.ticket.order.domain.OrderStatus;
import com.miaoyu.ticket.order.event.PaymentSucceededEvent;
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
 * 从A的PAID权威订单恢复可能遗漏的D出行任务。
 *
 * <ul>
 *   <li>每轮冻结同一个二十四小时窗口，并以paidTime和orderId稳定分页；</li>
 *   <li>候选进入跨模块调用前重读订单，拒绝状态或版本已经变化的数据；</li>
 *   <li>影院区域只经D公开摘要解析，不访问D的持久化实现；</li>
 *   <li>逐条调用D的幂等ensureTask，单条失败不终止后续候选或批次。</li>
 * </ul>
 *
 * <p>该服务不启动A的写事务、不修改任何交易状态，也不保存第二份任务完成标记。</p>
 */
@Service
public class PaidTravelTaskReconciliationService {

    private static final Logger LOGGER = LoggerFactory.getLogger(PaidTravelTaskReconciliationService.class);

    private final OrderRepository orderRepository;
    private final TravelEventContextResolver travelEventContextResolver;
    private final TravelTaskApplicationService travelTaskApplicationService;
    private final PaidTravelReconciliationProperties properties;
    private final Clock clock;

    /**
     * 依赖按所有权边界注入：A仓储提供订单事实，公共解析器组合A/D只读摘要，D应用服务
     * 独立承担任务幂等写入。Clock和有界配置使时间窗口及测试保持确定。
     *
     * @param orderRepository A的订单权威读取端口
     * @param travelEventContextResolver 不落库的场次和影院区域组合器
     * @param travelTaskApplicationService D公开的唯一任务确保入口
     * @param properties A的补偿窗口、分页和调度配置
     * @param clock 统一Asia/Shanghai业务时钟来源
     */
    public PaidTravelTaskReconciliationService(
            OrderRepository orderRepository,
            TravelEventContextResolver travelEventContextResolver,
            TravelTaskApplicationService travelTaskApplicationService,
            PaidTravelReconciliationProperties properties,
            Clock clock) {
        this.orderRepository = orderRepository;
        this.travelEventContextResolver = travelEventContextResolver;
        this.travelTaskApplicationService = travelTaskApplicationService;
        this.properties = properties;
        this.clock = clock;
    }

    /**
     * 扫描窗口在任务开始时冻结；本轮之后发生的新支付交给实时事件或下一轮处理。
     * 每页处理完成后才推进游标，因此某条失败不会让同页后续订单丢失。
     */
    public PaidTravelTaskReconciliationReport reconcilePaidOrders() {
        LocalDateTime windowEnd = currentBusinessTime();
        LocalDateTime windowStart = windowEnd.minusHours(properties.windowHours());
        ReconciliationCursor cursor = new ReconciliationCursor(windowStart, 0L);
        MutableReport report = new MutableReport();

        while (true) {
            List<OrderRepository.PaidTravelReconciliationCandidate> candidates =
                    orderRepository.findPaidTravelReconciliationCandidates(
                            windowStart,
                            windowEnd,
                            cursor.paidAt(),
                            cursor.orderId(),
                            properties.batchSize());
            if (candidates.isEmpty()) {
                break;
            }

            report.startBatch(candidates.size());
            for (OrderRepository.PaidTravelReconciliationCandidate candidate : candidates) {
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
     * 候选处理不传播运行时异常，使下一条订单和下一页仍能获得补偿机会。
     * 状态变化与上下文缺失属于可预期跳过；D或基础设施抛出的异常计为失败，二者在报告中
     * 分开统计，避免运维把正常并发变化误判为系统故障。
     */
    private void reconcileCandidate(
            OrderRepository.PaidTravelReconciliationCandidate candidate,
            MutableReport report) {
        try {
            OrderRepository.OrderSnapshot order = orderRepository.findById(candidate.orderId()).orElse(null);
            if (!isSamePaidVersion(order, candidate)) {
                report.skip();
                return;
            }

            Optional<TravelEventContextResolver.TravelEventContext> context =
                    travelEventContextResolver.resolve(order);
            if (context.isEmpty() || context.get().showId() != order.showId()) {
                report.skip();
                return;
            }

            travelTaskApplicationService.ensureTask(toPaymentEvent(order, candidate.paidAt(), context.get()));
            report.ensure();
        } catch (RuntimeException exception) {
            report.fail();
            LOGGER.warn(
                    "PAID出行任务补偿失败, orderId={}, errorType={}",
                    candidate.orderId(),
                    exception.getClass().getSimpleName(),
                    exception);
        }
    }

    /**
     * 状态和版本同时一致，才能把扫描快照重新表达为当前有效的支付事实。
     * 这里不加订单锁，因为持锁调用D会扩大本地事务；极短竞态最终由订单版本和退款取消事件收敛。
     */
    private boolean isSamePaidVersion(
            OrderRepository.OrderSnapshot order,
            OrderRepository.PaidTravelReconciliationCandidate candidate) {
        return order != null
                && order.status() == OrderStatus.PAID
                && order.version() == candidate.orderVersion();
    }

    /**
     * occurredAt保留原支付时间；新的eventId只标识本次补偿投递。
     * 事件不携带金额、座位、电子票或支付凭据，D也不得据此反向修改A的交易状态。
     */
    private PaymentSucceededEvent toPaymentEvent(
            OrderRepository.OrderSnapshot order,
            LocalDateTime paidAt,
            TravelEventContextResolver.TravelEventContext context) {
        return new PaymentSucceededEvent(
                UUID.randomUUID().toString(),
                Long.toString(order.orderId()),
                Long.toString(order.showId()),
                Long.toString(order.userId()),
                context.cinemaArea(),
                context.startAt().atZone(ClockConfiguration.BUSINESS_ZONE_ID).toOffsetDateTime(),
                order.version(),
                paidAt.atZone(ClockConfiguration.BUSINESS_ZONE_ID).toOffsetDateTime());
    }

    private LocalDateTime currentBusinessTime() {
        return LocalDateTime.ofInstant(clock.instant(), ClockConfiguration.BUSINESS_ZONE_ID);
    }

    /**
     * 键集游标必须严格前进，防止错误Repository实现让调度陷入无限循环。
     * 同一毫秒允许多个订单，只有paidAt相等且orderId更大时才算合法推进。
     */
    private record ReconciliationCursor(LocalDateTime paidAt, long orderId) {

        private ReconciliationCursor advanceTo(
                OrderRepository.PaidTravelReconciliationCandidate candidate) {
            boolean advanced = candidate.paidAt().isAfter(paidAt)
                    || (candidate.paidAt().isEqual(paidAt) && candidate.orderId() > orderId);
            if (!advanced) {
                throw new IllegalStateException("PAID出行补偿游标没有严格前进");
            }
            return new ReconciliationCursor(candidate.paidAt(), candidate.orderId());
        }
    }

    /**
     * 可变计数只存在于单次调用栈，最终对外返回不可变报告。
     * 该对象不会跨线程共享，也不作为恢复游标持久化，数据库和D唯一约束仍是最终事实。
     */
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

        private PaidTravelTaskReconciliationReport toReport() {
            return new PaidTravelTaskReconciliationReport(
                    batchCount,
                    scannedCount,
                    ensuredCount,
                    skippedCount,
                    failedCount);
        }
    }
}
