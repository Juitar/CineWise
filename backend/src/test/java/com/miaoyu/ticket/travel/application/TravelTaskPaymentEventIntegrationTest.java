package com.miaoyu.ticket.travel.application;

import static org.assertj.core.api.Assertions.assertThat;

import com.miaoyu.ticket.order.event.PaymentSucceededEvent;
import java.time.OffsetDateTime;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
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
}
