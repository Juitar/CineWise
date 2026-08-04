package com.miaoyu.ticket.travel.application;

import static org.assertj.core.api.Assertions.assertThat;

import com.miaoyu.ticket.order.event.PaymentSucceededEvent;
import java.time.Clock;
import java.time.OffsetDateTime;
import java.util.concurrent.ExecutionException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

/** 验证 D 只在发布事务提交后创建任务，且补偿与事件共用同一唯一任务。 */
@ActiveProfiles("test")
@SpringBootTest
class TravelTaskPaymentEventIntegrationTest {

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
    private PlatformTransactionManager transactionManager;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @BeforeEach
    void clearTravelTasks() {
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
        return new PaymentSucceededEvent(
                eventId,
                orderId,
                "66001",
                "55001",
                "西湖区",
                OffsetDateTime.parse("2026-08-05T19:00:00+08:00"),
                5L,
                OffsetDateTime.parse("2026-08-04T08:00:00+08:00"));
    }

    private long countTasks() {
        Long result = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM travel_task", Long.class);
        return result == null ? 0L : result;
    }

    private String triggerAtForOrder(long orderId) {
        return jdbcTemplate.queryForObject(
                "SELECT FORMATDATETIME(trigger_at, 'yyyy-MM-dd HH:mm:ss') FROM travel_task WHERE order_id = ?",
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
