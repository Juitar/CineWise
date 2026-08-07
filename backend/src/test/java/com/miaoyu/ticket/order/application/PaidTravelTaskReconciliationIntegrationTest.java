package com.miaoyu.ticket.order.application;

import static org.assertj.core.api.Assertions.assertThat;

import com.miaoyu.ticket.auth.application.CurrentUser;
import com.miaoyu.ticket.auth.application.CurrentUserAccessor;
import com.miaoyu.ticket.auth.application.RoleCode;
import com.miaoyu.ticket.order.domain.OrderStatus;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

/** 使用真实订单查询和D公开服务验证遗漏任务补建、重复幂等与窗口过滤。 */
@ActiveProfiles("test")
@SpringBootTest(properties = {
    "cinewise.seed.enabled=true",
    "cinewise.seed.fixed-value=20260802"
})
@Import(PaidTravelTaskReconciliationIntegrationTest.ReconciliationTestConfiguration.class)
@Disabled("待 A 在已执行 V013 的 MySQL 集成环境验证 cinema_id")
class PaidTravelTaskReconciliationIntegrationTest {

    private static final Instant FIXED_INSTANT = Instant.parse("2026-08-02T00:00:00Z");
    private static final LocalDateTime FIXED_LOCAL_TIME = LocalDateTime.of(2026, 8, 2, 8, 0);
    private static final long TEST_USER_ID = 9_800_001L;

    @Autowired
    private OrderApplicationService orderApplicationService;

    @Autowired
    private PaymentApplicationService paymentApplicationService;

    @Autowired
    private PaidTravelTaskReconciliationService reconciliationService;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @BeforeEach
    void resetTransactionAndTravelData() {
        jdbcTemplate.update("DELETE FROM travel_notification_log");
        jdbcTemplate.update("DELETE FROM travel_advice_snapshot");
        jdbcTemplate.update("DELETE FROM travel_task");
        jdbcTemplate.update("DELETE FROM ticket_order_operation");
        jdbcTemplate.update("DELETE FROM refund_request");
        jdbcTemplate.update("DELETE FROM electronic_ticket");
        jdbcTemplate.update("DELETE FROM mock_payment");
        jdbcTemplate.update("DELETE FROM ticket_order_seat");
        jdbcTemplate.update("DELETE FROM ticket_order");
        jdbcTemplate.update("""
                UPDATE show_seat
                   SET status = 'AVAILABLE',
                       lock_order_no = NULL,
                       lock_expire_time = NULL,
                       version = 0
                """);
    }

    @Test
    void givenPaidOrderTaskMissing_whenReconcileTwice_thenRebuildOneTaskWithoutChangingTransaction() {
        PaidOrder paidOrder = createPaidOrder("paid-reconciliation");
        assertThat(countTasks(paidOrder.order().orderId())).isOne();
        jdbcTemplate.update("DELETE FROM travel_task WHERE order_id = ?", paidOrder.order().orderId());

        PaidTravelTaskReconciliationReport first = reconciliationService.reconcilePaidOrders();
        PaidTravelTaskReconciliationReport repeated = reconciliationService.reconcilePaidOrders();

        assertThat(first.ensuredCount()).isEqualTo(1);
        assertThat(first.failedCount()).isZero();
        assertThat(repeated.ensuredCount()).isEqualTo(1);
        assertThat(countTasks(paidOrder.order().orderId())).isOne();
        assertThat(orderStatus(paidOrder.order().orderId())).isEqualTo(OrderStatus.PAID.name());
        assertThat(paymentCount(paidOrder.order().orderId())).isOne();
        assertThat(ticketCount(paidOrder.order().orderId())).isOne();
        assertThat(jdbcTemplate.queryForObject(
                "SELECT order_version FROM travel_task WHERE order_id = ?",
                Long.class,
                paidOrder.order().orderId())).isEqualTo((long) paidOrder.payment().stateVersion());
    }

    @Test
    void givenPaidTimeOutsideWindow_whenReconcile_thenDoNotRebuildTask() {
        PaidOrder paidOrder = createPaidOrder("paid-window");
        jdbcTemplate.update("DELETE FROM travel_task WHERE order_id = ?", paidOrder.order().orderId());
        jdbcTemplate.update(
                "UPDATE ticket_order SET paid_time = ? WHERE id = ?",
                FIXED_LOCAL_TIME.minusHours(25),
                paidOrder.order().orderId());

        PaidTravelTaskReconciliationReport report = reconciliationService.reconcilePaidOrders();

        assertThat(report.scannedCount()).isZero();
        assertThat(countTasks(paidOrder.order().orderId())).isZero();
        assertThat(orderStatus(paidOrder.order().orderId())).isEqualTo(OrderStatus.PAID.name());
    }

    private PaidOrder createPaidOrder(String prefix) {
        long showId = jdbcTemplate.queryForObject("""
                SELECT id
                  FROM movie_show
                 WHERE status = 'ON_SALE'
                   AND start_time > '2026-08-02 08:00:00'
                 ORDER BY start_time, id
                 LIMIT 1
                """, Long.class);
        long seatId = jdbcTemplate.queryForObject("""
                SELECT id
                  FROM show_seat
                 WHERE show_id = ?
                   AND status = 'AVAILABLE'
                 ORDER BY id
                 LIMIT 1
                """, Long.class, showId);
        OrderView order = orderApplicationService.createOrder(new CreateOrderCommand(
                showId,
                List.of(seatId),
                prefix + "-request",
                prefix + "-create-key"));
        PaymentView payment = paymentApplicationService.pay(order.orderNo(), prefix + "-payment-key");
        return new PaidOrder(order, payment);
    }

    private long countTasks(long orderId) {
        Long count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM travel_task WHERE order_id = ?",
                Long.class,
                orderId);
        return count == null ? 0L : count;
    }

    private long paymentCount(long orderId) {
        Long count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM mock_payment WHERE order_id = ?",
                Long.class,
                orderId);
        return count == null ? 0L : count;
    }

    private long ticketCount(long orderId) {
        Long count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM electronic_ticket WHERE order_id = ?",
                Long.class,
                orderId);
        return count == null ? 0L : count;
    }

    private String orderStatus(long orderId) {
        return jdbcTemplate.queryForObject(
                "SELECT status FROM ticket_order WHERE id = ?",
                String.class,
                orderId);
    }

    private record PaidOrder(OrderView order, PaymentView payment) {
    }

    @TestConfiguration(proxyBeanMethods = false)
    static class ReconciliationTestConfiguration {

        @Bean
        @Primary
        Clock fixedBusinessClock() {
            return Clock.fixed(FIXED_INSTANT, ZoneId.of("Asia/Shanghai"));
        }

        @Bean
        @Primary
        CurrentUserAccessor fixedCurrentUserAccessor() {
            return () -> new CurrentUser(TEST_USER_ID, RoleCode.USER, 0L);
        }
    }
}
