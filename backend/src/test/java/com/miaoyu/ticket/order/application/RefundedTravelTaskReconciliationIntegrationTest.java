package com.miaoyu.ticket.order.application;

import static org.assertj.core.api.Assertions.assertThat;

import com.miaoyu.ticket.auth.application.CurrentUser;
import com.miaoyu.ticket.auth.application.CurrentUserAccessor;
import com.miaoyu.ticket.auth.application.RoleCode;
import com.miaoyu.ticket.content.application.ContentSummaryQueryPort;
import com.miaoyu.ticket.order.domain.OrderStatus;
import com.miaoyu.ticket.ticketing.application.ShowContextQueryService;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

/** 使用真实A交易表和D公开服务验证退款取消恢复及PAID迟到竞态。 */
@ActiveProfiles("test")
@SpringBootTest(properties = {
    "cinewise.seed.enabled=true",
    "cinewise.seed.fixed-value=20260802"
})
@Import(RefundedTravelTaskReconciliationIntegrationTest.ReconciliationTestConfiguration.class)
class RefundedTravelTaskReconciliationIntegrationTest {

    private static final Instant FIXED_INSTANT = Instant.parse("2026-08-02T00:00:00Z");
    private static final LocalDateTime FIXED_LOCAL_TIME = LocalDateTime.of(2026, 8, 2, 8, 0);
    private static final long TEST_USER_ID = 9_810_001L;

    @Autowired
    private OrderApplicationService orderApplicationService;

    @Autowired
    private PaymentApplicationService paymentApplicationService;

    @Autowired
    private RefundApplicationService refundApplicationService;

    @Autowired
    private PaidTravelTaskReconciliationService paidReconciliationService;

    @Autowired
    private RefundedTravelTaskReconciliationService refundedReconciliationService;

    @Autowired
    private RaceControlledTravelEventContextResolver contextResolver;

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
        contextResolver.reset();
    }

    @Test
    void givenRefundCancellationTaskMissing_whenReconcileTwice_thenRestoreOneCancelledTombstone() {
        PaidOrder paidOrder = createPaidOrder("refunded-reconciliation");
        RefundView refund = refund(paidOrder.order().orderNo(), "refunded-reconciliation");
        assertThat(taskStatus(paidOrder.order().orderId())).isEqualTo("CANCELLED");
        jdbcTemplate.update("DELETE FROM travel_task WHERE order_id = ?", paidOrder.order().orderId());

        RefundedTravelTaskReconciliationReport first = refundedReconciliationService.reconcileRefundedOrders();
        RefundedTravelTaskReconciliationReport repeated = refundedReconciliationService.reconcileRefundedOrders();

        assertThat(first.ensuredCount()).isEqualTo(1);
        assertThat(first.failedCount()).isZero();
        assertThat(repeated.ensuredCount()).isEqualTo(1);
        assertThat(countTasks(paidOrder.order().orderId())).isOne();
        assertThat(taskStatus(paidOrder.order().orderId())).isEqualTo("CANCELLED");
        assertThat(taskOrderVersion(paidOrder.order().orderId())).isEqualTo(refund.stateVersion());
        assertThat(orderStatus(paidOrder.order().orderId())).isEqualTo(OrderStatus.REFUNDED.name());
        assertThat(refundCount(paidOrder.order().orderId())).isOne();
    }

    @Test
    void givenRefundOutsideWindow_whenReconcile_thenDoNotRestoreTask() {
        PaidOrder paidOrder = createPaidOrder("refunded-window");
        refund(paidOrder.order().orderNo(), "refunded-window");
        jdbcTemplate.update("DELETE FROM travel_task WHERE order_id = ?", paidOrder.order().orderId());
        jdbcTemplate.update(
                "UPDATE ticket_order SET refunded_time = ? WHERE id = ?",
                FIXED_LOCAL_TIME.minusHours(25),
                paidOrder.order().orderId());

        RefundedTravelTaskReconciliationReport report =
                refundedReconciliationService.reconcileRefundedOrders();

        assertThat(report.scannedCount()).isZero();
        assertThat(countTasks(paidOrder.order().orderId())).isZero();
        assertThat(orderStatus(paidOrder.order().orderId())).isEqualTo(OrderStatus.REFUNDED.name());
    }

    @Test
    void givenPaidCandidateReread_whenRefundCompletesBeforeEnsure_thenLatePaymentCannotReopenTask() {
        PaidOrder paidOrder = createPaidOrder("paid-refund-race");
        jdbcTemplate.update("DELETE FROM travel_task WHERE order_id = ?", paidOrder.order().orderId());
        AtomicReference<RefundView> refunded = new AtomicReference<>();
        contextResolver.runAfterNextResolve(() -> refunded.set(refund(
                paidOrder.order().orderNo(),
                "paid-refund-race")));

        PaidTravelTaskReconciliationReport paidReport = paidReconciliationService.reconcilePaidOrders();
        RefundedTravelTaskReconciliationReport refundedReport =
                refundedReconciliationService.reconcileRefundedOrders();

        assertThat(paidReport.ensuredCount()).isEqualTo(1);
        assertThat(refundedReport.ensuredCount()).isEqualTo(1);
        assertThat(refunded.get()).isNotNull();
        assertThat(orderStatus(paidOrder.order().orderId())).isEqualTo(OrderStatus.REFUNDED.name());
        assertThat(countTasks(paidOrder.order().orderId())).isOne();
        assertThat(taskStatus(paidOrder.order().orderId())).isEqualTo("CANCELLED");
        assertThat(taskOrderVersion(paidOrder.order().orderId()))
                .isGreaterThanOrEqualTo(refunded.get().stateVersion());
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

    private RefundView refund(String orderNo, String prefix) {
        return refundApplicationService.requestRefund(new RefundCommand(
                orderNo,
                "行程变化",
                prefix + "-refund-request",
                null,
                prefix + "-refund-key"));
    }

    private long countTasks(long orderId) {
        return count("SELECT COUNT(*) FROM travel_task WHERE order_id = ?", orderId);
    }

    private long refundCount(long orderId) {
        return count("SELECT COUNT(*) FROM refund_request WHERE order_id = ?", orderId);
    }

    private long count(String sql, long orderId) {
        Long count = jdbcTemplate.queryForObject(sql, Long.class, orderId);
        return count == null ? 0L : count;
    }

    private String orderStatus(long orderId) {
        return jdbcTemplate.queryForObject(
                "SELECT status FROM ticket_order WHERE id = ?",
                String.class,
                orderId);
    }

    private String taskStatus(long orderId) {
        return jdbcTemplate.queryForObject(
                "SELECT status FROM travel_task WHERE order_id = ?",
                String.class,
                orderId);
    }

    private long taskOrderVersion(long orderId) {
        return jdbcTemplate.queryForObject(
                "SELECT order_version FROM travel_task WHERE order_id = ?",
                Long.class,
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

        @Bean
        @Primary
        RaceControlledTravelEventContextResolver raceControlledTravelEventContextResolver(
                ShowContextQueryService showContextQueryService,
                ContentSummaryQueryPort contentSummaryQueryPort) {
            return new RaceControlledTravelEventContextResolver(
                    showContextQueryService,
                    contentSummaryQueryPort);
        }
    }

    /** 在解析完成后插入一次退款，精确制造PAID重读与ensureTask之间的竞态。 */
    static final class RaceControlledTravelEventContextResolver extends TravelEventContextResolver {

        private final AtomicReference<Runnable> afterNextResolve = new AtomicReference<>();

        RaceControlledTravelEventContextResolver(
                ShowContextQueryService showContextQueryService,
                ContentSummaryQueryPort contentSummaryQueryPort) {
            super(showContextQueryService, contentSummaryQueryPort);
        }

        @Override
        public Optional<TravelEventContext> resolve(OrderRepository.OrderSnapshot order) {
            Optional<TravelEventContext> context = super.resolve(order);
            Runnable callback = afterNextResolve.getAndSet(null);
            if (callback != null) {
                callback.run();
            }
            return context;
        }

        void runAfterNextResolve(Runnable callback) {
            assertThat(afterNextResolve.compareAndSet(null, callback)).isTrue();
        }

        void reset() {
            afterNextResolve.set(null);
        }
    }
}
