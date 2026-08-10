package com.miaoyu.ticket.travel.application;

import static org.assertj.core.api.Assertions.assertThat;

import com.miaoyu.ticket.order.event.OrderInvalidated;
import com.miaoyu.ticket.order.event.PaymentSucceededEvent;
import com.miaoyu.ticket.travel.TravelTestProfileConsentConfiguration;
import com.miaoyu.ticket.common.config.ClockConfiguration;
import java.time.Clock;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.ExecutionException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

/** 验证 D 只在发布事务提交后创建任务，且补偿与事件共用同一唯一任务。 */
@EnabledIfEnvironmentVariable(named = "CINEWISE_MYSQL_TRAVEL_IT", matches = "true")
@ActiveProfiles("dev")
@SpringBootTest(properties = {
    "spring.flyway.enabled=true",
    "cinewise.seed.enabled=true",
    "cinewise.seed.fixed-value=20260802",
    "cinewise.transaction.expiry-job-enabled=false",
    "cinewise.transaction.paid-travel-reconciliation.enabled=false",
    "cinewise.transaction.refunded-travel-reconciliation.enabled=false",
    "management.health.redis.enabled=false"
})
@Import(TravelTestProfileConsentConfiguration.class)
class TravelTaskPaymentEventIntegrationTest {

    private static final String REQUIRED_DATABASE = "cinewise_ticketing_concurrency_check";

    @Autowired
    private ApplicationEventPublisher eventPublisher;

    @Autowired
    private TransactionTemplate transactionTemplate;

    @Autowired
    private TravelTaskApplicationService travelTaskApplicationService;

    @Autowired
    private TravelAdviceService travelAdviceService;

    @Autowired
    private TravelAdviceRepository travelAdviceRepository;

    @Autowired
    private TravelReminderSchedulingService travelReminderSchedulingService;

    @Autowired
    private PlatformTransactionManager transactionManager;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private Clock clock;

    @BeforeEach
    void clearTravelTasks() {
        assertThat(jdbcTemplate.queryForObject("SELECT DATABASE()", String.class))
                .as("出行事件 MySQL 测试只允许操作隔离库")
                .isEqualTo(REQUIRED_DATABASE);
        jdbcTemplate.update("DELETE FROM travel_notification_log");
        jdbcTemplate.update("DELETE FROM travel_advice_snapshot");
        jdbcTemplate.update("DELETE FROM travel_task");
    }

    @Test
    void givenCommittedPaymentEvent_whenPublished_thenCreateOneTaskAndCompensationReturnsIt() {
        PaymentSucceededEvent original = paymentEvent("event-committed", "88001");
        transactionTemplate.executeWithoutResult(status -> eventPublisher.publishEvent(original));

        TravelTaskSummary compensated = travelTaskApplicationService.ensureTask(paymentEvent("event-rebuilt", "88001"));

        assertThat(countTasks()).isOne();
        assertThat(compensated.status()).hasToString("PENDING");
        assertThat(compensated.orderVersion()).isEqualTo(5L);
        assertThat(triggerAtForOrder(88001L)).isEqualTo("2026-08-05 17:00:00");
    }

    @Test
    void givenValidCinemaId_whenPaymentTaskCreated_thenPersistCinemaId() {
        TravelTaskSummary task = travelTaskApplicationService.ensureTask(
                paymentEvent("event-cinema-id", "88012", "44001"));

        assertThat(jdbcTemplate.queryForObject(
                "SELECT cinema_id FROM travel_task WHERE task_id = ?", Long.class, task.taskId()))
                .isEqualTo(44001L);
    }

    @Test
    void givenRolledBackPaymentEvent_whenPublished_thenDoNotCreateTask() {
        try {
            transactionTemplate.executeWithoutResult(status -> {
                eventPublisher.publishEvent(paymentEvent("event-rolled-back", "88002"));
                status.setRollbackOnly();
            });
        } catch (RuntimeException exception) {
            throw new AssertionError("仅设置事务回滚不应向测试抛异常", exception);
        }

        assertThat(countTasks()).isZero();
    }

    @Test
    void givenCommittedRefundEvent_whenPublished_thenCancelTaskAndKeepAdviceReadOnly() {
        TravelTaskSummary task = travelTaskApplicationService.ensureTask(paymentEvent("payment-refund", "88005"));
        long internalTaskId = jdbcTemplate.queryForObject(
                "SELECT id FROM travel_task WHERE task_id = ?", Long.class, task.taskId());
        travelAdviceService.generate(internalTaskId);

        transactionTemplate.executeWithoutResult(status -> eventPublisher.publishEvent(
                invalidatedEvent("refund-committed", "88005", 6L)));

        assertThat(jdbcTemplate.queryForObject(
                "SELECT status FROM travel_task WHERE id = ?", String.class, internalTaskId))
                .isEqualTo("CANCELLED");
        assertThat(jdbcTemplate.queryForObject(
                "SELECT closed_at IS NOT NULL FROM travel_task WHERE id = ?", Boolean.class, internalTaskId))
                .isTrue();
        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM travel_advice_snapshot WHERE travel_task_id = ?",
                Long.class,
                internalTaskId)).isOne();
    }

    @Test
    void givenRefundEventBeforePayment_whenCompensatedPaymentArrives_thenKeepCancelledTombstone() {
        transactionTemplate.executeWithoutResult(status -> eventPublisher.publishEvent(
                invalidatedEvent("refund-first", "88006", 7L)));

        TravelTaskSummary paymentResult = travelTaskApplicationService.ensureTask(
                paymentEvent("payment-late", "88006"));

        assertThat(paymentResult.status()).hasToString("CANCELLED");
        assertThat(countTasks()).isOne();
    }

    @Test
    void givenInvalidCinemaIdOnEarlyRefund_whenCompensatedPaymentArrives_thenKeepNullTombstone() {
        travelTaskApplicationService.ensureTaskCancelled(
                invalidatedEvent("refund-invalid-cinema", "88013", 4L, "0"));

        travelTaskApplicationService.ensureTask(paymentEvent(
                "payment-after-invalid-refund", "88013", "44001"));

        assertThat(jdbcTemplate.queryForObject(
                "SELECT cinema_id FROM travel_task WHERE order_id = ?", Long.class, 88013L)).isNull();
        assertThat(jdbcTemplate.queryForObject(
                "SELECT status FROM travel_task WHERE order_id = ?", String.class, 88013L))
                .isEqualTo("CANCELLED");
    }

    @Test
    void givenLowerRefundThenHigherRefund_whenPublished_thenPromoteCancelledTombstoneAuditVersion() {
        transactionTemplate.executeWithoutResult(status -> eventPublisher.publishEvent(
                invalidatedEvent("refund-v5", "88007", 5L)));
        transactionTemplate.executeWithoutResult(status -> eventPublisher.publishEvent(
                invalidatedEvent("refund-v6", "88007", 6L)));

        assertThat(jdbcTemplate.queryForObject(
                "SELECT status FROM travel_task WHERE order_id = ?", String.class, 88007L))
                .isEqualTo("CANCELLED");
        assertThat(jdbcTemplate.queryForObject(
                "SELECT order_version FROM travel_task WHERE order_id = ?", Long.class, 88007L))
                .isEqualTo(6L);
        assertThat(jdbcTemplate.queryForObject(
                "SELECT invalidation_event_id FROM travel_task WHERE order_id = ?", String.class, 88007L))
                .isEqualTo("refund-v6");
    }

    @Test
    void givenConcurrentRefunds_whenCancelling_thenKeepHigherCancelledTombstoneVersion() throws Exception {
        CountDownLatch start = new CountDownLatch(1);
        try (ExecutorService executor = Executors.newFixedThreadPool(2)) {
            Future<TravelTaskSummary> lower = executor.submit(() -> cancelAfterSignal(
                    start, invalidatedEvent("refund-concurrent-v5", "88008", 5L)));
            Future<TravelTaskSummary> higher = executor.submit(() -> cancelAfterSignal(
                    start, invalidatedEvent("refund-concurrent-v6", "88008", 6L)));
            start.countDown();

            lower.get();
            higher.get();
        }

        assertThat(jdbcTemplate.queryForObject(
                "SELECT status FROM travel_task WHERE order_id = ?", String.class, 88008L))
                .isEqualTo("CANCELLED");
        assertThat(jdbcTemplate.queryForObject(
                "SELECT order_version FROM travel_task WHERE order_id = ?", Long.class, 88008L))
                .isEqualTo(6L);
    }

    @Test
    void givenPendingTask_whenGeneratingAdvice_thenAppendDemoSnapshotAndMarkTaskReady() {
        TravelTaskSummary task = travelTaskApplicationService.ensureTask(paymentEvent("event-advice", "88003"));
        long internalTaskId = jdbcTemplate.queryForObject(
                "SELECT id FROM travel_task WHERE task_id = ?", Long.class, task.taskId());

        TravelAdviceSnapshot snapshot = travelAdviceService.generate(internalTaskId);

        assertThat(snapshot.source()).isEqualTo("DEMO_WEATHER_V1");
        assertThat(snapshot.degraded()).isTrue();
        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM travel_advice_snapshot WHERE travel_task_id = ? AND task_version = ?",
                Long.class, internalTaskId, snapshot.taskVersion())).isEqualTo(1L);
        assertThat(jdbcTemplate.queryForObject(
                "SELECT status FROM travel_task WHERE id = ?", String.class, internalTaskId)).isEqualTo("READY");
    }

    @Test
    void givenDueTask_whenSchedulerRunsRepeatedly_thenAppendOnlyOneAdviceSnapshot() {
        TravelTaskSummary task = travelTaskApplicationService.ensureTask(
                paymentEvent("scheduled-advice", "88009"));
        long internalTaskId = jdbcTemplate.queryForObject(
                "SELECT id FROM travel_task WHERE task_id = ?", Long.class, task.taskId());
        // 该用例只验证到期提醒的去重，不应因固定历史 start_at 被完成清理分支抢先关闭。
        LocalDateTime futureStartAt = LocalDateTime.ofInstant(clock.instant(), ClockConfiguration.BUSINESS_ZONE_ID)
                .plusDays(1);
        jdbcTemplate.update("UPDATE travel_task SET start_at = ? WHERE id = ?", futureStartAt, internalTaskId);
        jdbcTemplate.update(
                "UPDATE travel_task SET trigger_at = DATE_SUB(CURRENT_TIMESTAMP, INTERVAL 1 MINUTE) "
                        + "WHERE id = ?",
                internalTaskId);

        travelReminderSchedulingService.runDueTasks();
        travelReminderSchedulingService.runDueTasks();

        // 第二次调度已看不到 PENDING 候选，且版本条件更新不允许重复快照。
        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM travel_advice_snapshot WHERE travel_task_id = ?", Long.class, internalTaskId))
                .isEqualTo(1L);
        assertThat(jdbcTemplate.queryForObject(
                "SELECT status FROM travel_task WHERE id = ?", String.class, internalTaskId)).isEqualTo("READY");
    }

    @Test
    void givenCancelledOrElapsedTask_whenSchedulerRuns_thenDoNotGenerateAndCloseElapsedTask() {
        TravelTaskSummary cancelled = travelTaskApplicationService.ensureTask(
                paymentEvent("scheduled-cancel", "88010"));
        transactionTemplate.executeWithoutResult(status -> eventPublisher.publishEvent(
                invalidatedEvent("scheduled-cancelled", "88010", 6L)));
        long cancelledId = jdbcTemplate.queryForObject(
                "SELECT id FROM travel_task WHERE task_id = ?", Long.class, cancelled.taskId());
        TravelTaskSummary elapsed = travelTaskApplicationService.ensureTask(
                paymentEvent("scheduled-complete", "88011"));
        long elapsedId = jdbcTemplate.queryForObject(
                "SELECT id FROM travel_task WHERE task_id = ?", Long.class, elapsed.taskId());
        travelAdviceService.generate(elapsedId);
        jdbcTemplate.update(
                "UPDATE travel_task SET start_at = DATE_SUB(CURRENT_TIMESTAMP, INTERVAL 3 HOUR) "
                        + "WHERE id = ?",
                elapsedId);

        travelReminderSchedulingService.runDueTasks();

        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM travel_advice_snapshot WHERE travel_task_id = ?", Long.class, cancelledId))
                .isZero();
        assertThat(jdbcTemplate.queryForObject(
                "SELECT status FROM travel_task WHERE id = ?", String.class, elapsedId)).isEqualTo("COMPLETED");
    }

    @Test
    void givenStartedPendingTask_whenSchedulerRuns_thenDoNotGenerateAdviceBeforeCompletionWindow() {
        TravelTaskSummary task = travelTaskApplicationService.ensureTask(
                paymentEvent("scheduled-started", "88014"));
        long internalTaskId = jdbcTemplate.queryForObject(
                "SELECT id FROM travel_task WHERE task_id = ?", Long.class, task.taskId());
        LocalDateTime now = LocalDateTime.ofInstant(clock.instant(), ClockConfiguration.BUSINESS_ZONE_ID);
        jdbcTemplate.update(
                "UPDATE travel_task SET start_at = ?, trigger_at = ? WHERE id = ?",
                now.minusHours(1),
                now.minusMinutes(1),
                internalTaskId);

        travelReminderSchedulingService.runDueTasks();

        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM travel_advice_snapshot WHERE travel_task_id = ?", Long.class, internalTaskId))
                .isZero();
        assertThat(jdbcTemplate.queryForObject(
                "SELECT status FROM travel_task WHERE id = ?", String.class, internalTaskId))
                .isEqualTo("PENDING");
    }

    @Test
    void givenEarlierRepeatableReadTransaction_whenWinnerCommits_thenFreshReadFindsSnapshot() {
        TravelTaskSummary task = travelTaskApplicationService.ensureTask(paymentEvent("event-multi-instance", "88004"));
        long internalTaskId = jdbcTemplate.queryForObject(
                "SELECT id FROM travel_task WHERE task_id = ?", Long.class, task.taskId());
        TransactionTemplate repeatableRead = new TransactionTemplate(transactionManager);
        repeatableRead.setIsolationLevel(TransactionDefinition.ISOLATION_REPEATABLE_READ);
        TravelAdviceSnapshot recovered = repeatableRead.execute(status -> {
            // 先建立外层可重复读事务的旧读取视图，再由独立服务事务完成赢家写入。
            assertThat(travelAdviceRepository.findByTaskIdAndVersion(internalTaskId, 1L)).isEmpty();
            TravelAdviceSnapshot winner = runWinnerInSeparateTransaction(internalTaskId);
            assertThat(winner.taskVersion()).isEqualTo(1L);
            return travelAdviceRepository.findCommittedByTaskIdAndVersion(internalTaskId, 1L)
                    .orElseThrow(() -> new AssertionError("独立事务未读取到赢家快照"));
        });

        assertThat(recovered.taskVersion()).isEqualTo(1L);
    }

    private PaymentSucceededEvent paymentEvent(String eventId, String orderId) {
        return paymentEvent(eventId, orderId, "44001");
    }

    private PaymentSucceededEvent paymentEvent(String eventId, String orderId, String cinemaId) {
        return new PaymentSucceededEvent(
                eventId,
                orderId,
                "66001",
                "77001",
                cinemaId,
                "55001",
                "西湖区",
                OffsetDateTime.parse("2026-08-05T19:00:00+08:00"),
                5L,
                OffsetDateTime.parse("2026-08-04T08:00:00+08:00"));
    }

    private OrderInvalidated invalidatedEvent(String eventId, String orderId, long orderVersion) {
        return invalidatedEvent(eventId, orderId, orderVersion, "44001");
    }

    private OrderInvalidated invalidatedEvent(
            String eventId, String orderId, long orderVersion, String cinemaId) {
        return new OrderInvalidated(
                eventId,
                orderId,
                "66001",
                cinemaId,
                "55001",
                "西湖区",
                OffsetDateTime.parse("2026-08-05T19:00:00+08:00"),
                orderVersion,
                OffsetDateTime.parse("2026-08-04T08:10:00+08:00"),
                "REFUNDED");
    }

    private TravelTaskSummary cancelAfterSignal(CountDownLatch start, OrderInvalidated event)
            throws InterruptedException {
        start.await();
        return travelTaskApplicationService.ensureTaskCancelled(event);
    }

    private long countTasks() {
        Long result = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM travel_task", Long.class);
        return result == null ? 0L : result;
    }

    private String triggerAtForOrder(long orderId) {
        return jdbcTemplate.queryForObject(
                "SELECT DATE_FORMAT(trigger_at, '%Y-%m-%d %H:%i:%s') FROM travel_task WHERE order_id = ?",
                String.class,
                orderId);
    }

    private TravelAdviceSnapshot runWinnerInSeparateTransaction(long taskId) {
        try (var executor = java.util.concurrent.Executors.newSingleThreadExecutor()) {
            return executor.submit(() -> travelAdviceService.generate(taskId)).get();
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new AssertionError("赢家事务被中断", exception);
        } catch (ExecutionException exception) {
            throw new AssertionError("赢家事务生成建议失败", exception.getCause());
        }
    }
}
