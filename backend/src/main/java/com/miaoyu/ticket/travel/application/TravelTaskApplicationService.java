package com.miaoyu.ticket.travel.application;

import com.miaoyu.ticket.common.config.ClockConfiguration;
import com.miaoyu.ticket.common.id.BusinessIdGenerator;
import com.miaoyu.ticket.order.event.OrderInvalidated;
import com.miaoyu.ticket.order.event.PaymentSucceededEvent;
import com.miaoyu.ticket.travel.domain.TravelTaskStatus;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.Objects;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * 支付后出行任务的公开应用服务。
 *
 * <p>支付已经提交后才会进入此服务，因此这里的失败不得回滚订单。每次调用使用独立事务，既避免占用
 * A 的支付事务，也让事件监听失败能由 A 的对账调用使用同一入口恢复。</p>
 */
@Service
public class TravelTaskApplicationService {

    private static final long REMINDER_ADVANCE_HOURS = 2L;

    private final TravelTaskRepository travelTaskRepository;
    private final BusinessIdGenerator idGenerator;
    private final Clock clock;

    public TravelTaskApplicationService(
            TravelTaskRepository travelTaskRepository,
            BusinessIdGenerator idGenerator,
            Clock clock) {
        this.travelTaskRepository = travelTaskRepository;
        this.idGenerator = idGenerator;
        this.clock = clock;
    }

    /**
     * 按支付事件创建或恢复唯一任务，供监听器和 A 的 PAID 对账共同调用。
     *
     * <p>先按 eventId 查询能识别同一投递重放；再按 orderId 查询能覆盖首次监听失败后由 A 重建的
     * 新 eventId。最终仍以数据库两个唯一键裁决并发，不能只依赖这两次查询。</p>
     *
     * @param event A 在支付事务中登记的完整事件
     * @return 原有或新建任务的最小摘要
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public TravelTaskSummary ensureTask(PaymentSucceededEvent event) {
        PaymentTaskInput input = PaymentTaskInput.from(event);

        return travelTaskRepository.findByPaymentEventId(input.eventId())
                .or(() -> travelTaskRepository.findByOrderId(input.orderId()))
                .map(this::toSummary)
                .orElseGet(() -> insertOrRecover(input));
    }

    /**
     * 按退款事件取消任务，供退款监听器和 A 的 REFUNDED 对账共同调用。
     *
     * <p>退款可能早于支付事件抵达。此时必须先保留 CANCELLED 墓碑，后续支付补偿按 orderId 只能读回
     * 墓碑，绝不能重新生成提醒。已有任务则由数据库条件更新比较订单版本，旧退款不会影响较新任务。</p>
     *
     * @param event A 在退款完成后登记的最小失效事件
     * @return 取消后的或原有的最小任务摘要
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public TravelTaskSummary ensureTaskCancelled(OrderInvalidated event) {
        InvalidationTaskInput input = InvalidationTaskInput.from(event);

        return travelTaskRepository.findByInvalidationEventId(input.eventId())
                .map(this::toSummary)
                .orElseGet(() -> cancelOrCreateTombstone(input));
    }

    private TravelTaskSummary insertOrRecover(PaymentTaskInput input) {
        LocalDateTime createdAt = currentBusinessTime();
        long internalId = idGenerator.nextId();
        TravelTaskRepository.NewTravelTask task = new TravelTaskRepository.NewTravelTask(
                internalId,
                Long.toString(internalId),
                input.eventId(),
                input.userId(),
                input.orderId(),
                input.showId(),
                input.cinemaArea(),
                input.startAt(),
                input.startAt().minusHours(REMINDER_ADVANCE_HOURS),
                input.orderVersion(),
                createdAt);
        try {
            travelTaskRepository.insert(task);
            return new TravelTaskSummary(task.taskId(), TravelTaskStatus.PENDING, task.orderVersion());
        } catch (DuplicateKeyException exception) {
            // 两个线程都尚未查到任务时，由唯一键留下赢家；输家只能读取既有结果，不能再次插入。
            return travelTaskRepository.findByOrderId(input.orderId())
                    .map(this::toSummary)
                    .orElseThrow(() -> new IllegalStateException("出行任务唯一键冲突后未找到原任务", exception));
        }
    }

    private TravelTaskSummary cancelOrCreateTombstone(InvalidationTaskInput input) {
        return travelTaskRepository.findByOrderId(input.orderId())
                .map(task -> cancelExistingTask(task, input))
                .orElseGet(() -> insertCancelledOrRecover(input));
    }

    private TravelTaskSummary cancelExistingTask(
            TravelTaskRepository.TravelTaskSnapshot task, InvalidationTaskInput input) {
        if (task.orderVersion() > input.orderVersion()
                || task.status() == TravelTaskStatus.COMPLETED
                || task.status() == TravelTaskStatus.FAILED
                || (task.status() == TravelTaskStatus.CANCELLED && task.orderVersion() == input.orderVersion())) {
            return toSummary(task);
        }
        if (travelTaskRepository.cancel(task.id(), input.orderVersion(), input.eventId(), currentBusinessTime())) {
            return new TravelTaskSummary(task.taskId(), TravelTaskStatus.CANCELLED, input.orderVersion());
        }
        // 条件更新失败说明另一个实例已更新状态或版本，必须读取数据库结果，不能用旧内存对象返回。
        return travelTaskRepository.findByOrderId(input.orderId())
                .map(this::toSummary)
                .orElseThrow(() -> new IllegalStateException("出行任务取消竞争后未找到原任务"));
    }

    private TravelTaskSummary insertCancelledOrRecover(InvalidationTaskInput input) {
        LocalDateTime closedAt = currentBusinessTime();
        long internalId = idGenerator.nextId();
        TravelTaskRepository.NewCancelledTravelTask task = new TravelTaskRepository.NewCancelledTravelTask(
                internalId,
                Long.toString(internalId),
                input.eventId(),
                input.userId(),
                input.orderId(),
                input.showId(),
                input.cinemaArea(),
                input.startAt(),
                input.startAt().minusHours(REMINDER_ADVANCE_HOURS),
                input.orderVersion(),
                closedAt);
        try {
            travelTaskRepository.insertCancelled(task);
            return new TravelTaskSummary(task.taskId(), TravelTaskStatus.CANCELLED, task.orderVersion());
        } catch (DuplicateKeyException exception) {
            // 支付和退款可能并发插入同一 orderId；唯一键赢家后续仍要按退款版本完成取消。
            return travelTaskRepository.findByInvalidationEventId(input.eventId())
                    .or(() -> travelTaskRepository.findByOrderId(input.orderId()))
                    .map(existing -> cancelExistingTask(existing, input))
                    .orElseThrow(() -> new IllegalStateException("出行取消墓碑唯一键冲突后未找到原任务", exception));
        }
    }

    private TravelTaskSummary toSummary(TravelTaskRepository.TravelTaskSnapshot task) {
        return new TravelTaskSummary(task.taskId(), task.status(), task.orderVersion());
    }

    private LocalDateTime currentBusinessTime() {
        return LocalDateTime.ofInstant(clock.instant(), ClockConfiguration.BUSINESS_ZONE_ID);
    }

    /**
     * 将 A 的字符串事件字段在边界处一次性校验和转换。
     *
     * <p>数据库保存内部 BIGINT，REST 和事件仍使用字符串；若 A 的事件被错误改坏，应尽早失败并由
     * 对账发现，而不是让 MyBatis 隐式转换成错误的订单关联。</p>
     */
    private record PaymentTaskInput(
            String eventId,
            long orderId,
            long showId,
            long userId,
            String cinemaArea,
            LocalDateTime startAt,
            long orderVersion) {

        private static PaymentTaskInput from(PaymentSucceededEvent event) {
            Objects.requireNonNull(event, "payment event 不能为空");
            if (event.orderVersion() < 0) {
                throw new IllegalArgumentException("payment event 的 orderVersion 不能为负数");
            }
            return new PaymentTaskInput(
                    requiredText(event.eventId(), "eventId"),
                    parseBusinessId(event.orderId(), "orderId"),
                    parseBusinessId(event.showId(), "showId"),
                    parseBusinessId(event.userId(), "userId"),
                    requiredText(event.cinemaArea(), "cinemaArea"),
                    Objects.requireNonNull(event.startAt(), "startAt 不能为空")
                            .atZoneSameInstant(ClockConfiguration.BUSINESS_ZONE_ID)
                            .toLocalDateTime(),
                    event.orderVersion());
        }

        private static String requiredText(String value, String fieldName) {
            if (value == null || value.isBlank()) {
                throw new IllegalArgumentException(fieldName + " 不能为空");
            }
            return value.trim();
        }

        private static long parseBusinessId(String value, String fieldName) {
            try {
                long parsed = Long.parseLong(requiredText(value, fieldName));
                if (parsed <= 0) {
                    throw new IllegalArgumentException(fieldName + " 必须是正整数");
                }
                return parsed;
            } catch (NumberFormatException exception) {
                throw new IllegalArgumentException(fieldName + " 必须是十进制业务ID", exception);
            }
        }
    }

    /** 将退款事件校验为任务取消所需的内部数据，字段规则与支付事件保持一致。 */
    private record InvalidationTaskInput(
            String eventId,
            long orderId,
            long showId,
            long userId,
            String cinemaArea,
            LocalDateTime startAt,
            long orderVersion) {

        private static InvalidationTaskInput from(OrderInvalidated event) {
            Objects.requireNonNull(event, "order invalidated event 不能为空");
            if (event.orderVersion() < 0) {
                throw new IllegalArgumentException("order invalidated event 的 orderVersion 不能为负数");
            }
            if (!"REFUNDED".equals(event.invalidReason())) {
                throw new IllegalArgumentException("order invalidated event 的 invalidReason 必须为 REFUNDED");
            }
            return new InvalidationTaskInput(
                    PaymentTaskInput.requiredText(event.eventId(), "eventId"),
                    PaymentTaskInput.parseBusinessId(event.orderId(), "orderId"),
                    PaymentTaskInput.parseBusinessId(event.showId(), "showId"),
                    PaymentTaskInput.parseBusinessId(event.userId(), "userId"),
                    PaymentTaskInput.requiredText(event.cinemaArea(), "cinemaArea"),
                    Objects.requireNonNull(event.startAt(), "startAt 不能为空")
                            .atZoneSameInstant(ClockConfiguration.BUSINESS_ZONE_ID)
                            .toLocalDateTime(),
                    event.orderVersion());
        }
    }
}
