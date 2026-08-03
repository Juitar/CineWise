package com.miaoyu.ticket.order.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.miaoyu.ticket.auth.application.CurrentUser;
import com.miaoyu.ticket.auth.application.CurrentUserAccessor;
import com.miaoyu.ticket.auth.application.RoleCode;
import com.miaoyu.ticket.common.error.BusinessException;
import com.miaoyu.ticket.order.domain.ElectronicTicketStatus;
import com.miaoyu.ticket.order.domain.OrderStatus;
import com.miaoyu.ticket.order.domain.RefundStatus;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
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

@ActiveProfiles("test")
@SpringBootTest(properties = {
    "cinewise.seed.enabled=true",
    "cinewise.seed.fixed-value=20260802"
})
@Import(RefundIntegrationTest.RefundTestConfiguration.class)
class RefundIntegrationTest {

    private static final Instant FIXED_INSTANT = Instant.parse("2026-08-02T00:00:00Z");
    private static final LocalDateTime FIXED_LOCAL_TIME = LocalDateTime.of(2026, 8, 2, 8, 0);
    private static final long USER_A = 9_600_001L;
    private static final long USER_B = 9_600_002L;

    @Autowired
    private OrderApplicationService orderApplicationService;

    @Autowired
    private PaymentApplicationService paymentApplicationService;

    @Autowired
    private RefundApplicationService refundApplicationService;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private RefundCurrentUserAccessor currentUserAccessor;

    @BeforeEach
    void resetTransactionData() {
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
        currentUserAccessor.useUser(USER_A);
    }

    @Test
    void givenPaidOrder_whenQueryImpactRefundReplayAndRecover_thenReturnOneRefund() {
        PaidOrder paidOrder = createPaidOrder("refund-success", 2);

        RefundImpactView impact = refundApplicationService.queryImpact(paidOrder.order().orderNo());
        RefundView refunded = refundApplicationService.requestRefund(command(
                paidOrder.order().orderNo(),
                "refund-request-1",
                "行程变化",
                "refund-key-1"));
        RefundView replayOriginal = refundApplicationService.requestRefund(command(
                paidOrder.order().orderNo(),
                "refund-request-1",
                "行程变化",
                "refund-key-1"));
        RefundView replayNewKey = refundApplicationService.requestRefund(command(
                paidOrder.order().orderNo(),
                "different-request-is-ignored-for-new-key",
                "其他原因",
                "refund-key-2"));
        RefundView recovered = refundApplicationService.queryRefund(paidOrder.order().orderNo());

        assertThat(impact.refundAmount()).isEqualByComparingTo(paidOrder.order().totalAmount());
        assertThat(impact.orderStatus()).isEqualTo(OrderStatus.PAID);
        assertThat(impact.ticketStatus()).isEqualTo(ElectronicTicketStatus.VALID);
        assertThat(refunded.refundStatus()).isEqualTo(RefundStatus.SUCCESS);
        assertThat(refunded.orderStatus()).isEqualTo(OrderStatus.REFUNDED);
        assertThat(refunded.ticketStatus()).isEqualTo(ElectronicTicketStatus.REFUNDED);
        assertThat(replayOriginal.refundNo()).isEqualTo(refunded.refundNo());
        assertThat(replayNewKey.refundNo()).isEqualTo(refunded.refundNo());
        assertThat(recovered).isEqualTo(refunded);
        assertThat(countRows("refund_request")).isEqualTo(1);
        assertThat(paidOrder.order().seatIds()).allSatisfy(
                seatId -> assertThat(seatStatus(seatId)).isEqualTo("AVAILABLE"));
    }

    @Test
    void givenRefundKeyBound_whenChangeParametersOrOrder_thenRejectMismatch() {
        PaidOrder first = createPaidOrder("refund-first", 1);
        PaidOrder second = createPaidOrder("refund-second", 1);
        refundApplicationService.requestRefund(command(
                first.order().orderNo(),
                "refund-first-request",
                "原因A",
                "shared-refund-key"));

        assertMismatch(() -> refundApplicationService.requestRefund(command(
                first.order().orderNo(),
                "changed-request",
                "原因A",
                "shared-refund-key")));
        assertMismatch(() -> refundApplicationService.requestRefund(command(
                second.order().orderNo(),
                "refund-second-request",
                "原因B",
                "shared-refund-key")));
        assertThat(orderStatus(second.order().orderId())).isEqualTo("PAID");
        assertThat(countRows("refund_request")).isEqualTo(1);
    }

    @Test
    void givenStartedShowOrAgentAction_whenRefund_thenRejectWithoutWrites() {
        PaidOrder started = createPaidOrder("refund-started", 1);
        jdbcTemplate.update(
                "UPDATE movie_show SET start_time = ? WHERE id = ?",
                FIXED_LOCAL_TIME,
                started.order().showId());

        assertThatThrownBy(() -> refundApplicationService.queryImpact(started.order().orderNo()))
                .isInstanceOfSatisfying(BusinessException.class, exception -> assertThat(exception.getErrorCode())
                        .isEqualTo(OrderErrorCode.ORDER_NOT_REFUNDABLE));
        assertThatThrownBy(() -> refundApplicationService.requestRefund(new RefundCommand(
                started.order().orderNo(),
                null,
                "agent-request",
                "unverified-action-id",
                "agent-key")))
                .isInstanceOfSatisfying(BusinessException.class, exception -> assertThat(exception.getErrorCode())
                        .isEqualTo(OrderErrorCode.CONFIRMATION_INVALID));
        assertThat(countRows("refund_request")).isZero();
        assertThat(orderStatus(started.order().orderId())).isEqualTo("PAID");
    }

    @Test
    void givenTicketOrSeatInconsistent_whenRefund_thenRollbackAllAuthoritativeStates() {
        PaidOrder ticketMismatch = createPaidOrder("refund-ticket-mismatch", 1);
        jdbcTemplate.update(
                "UPDATE electronic_ticket SET status = 'INVALIDATED' WHERE order_id = ?",
                ticketMismatch.order().orderId());
        assertNotRefundable(() -> refundApplicationService.requestRefund(command(
                ticketMismatch.order().orderNo(),
                "ticket-mismatch-request",
                null,
                "ticket-mismatch-key")));

        PaidOrder seatMismatch = createPaidOrder("refund-seat-mismatch", 2);
        jdbcTemplate.update(
                "UPDATE show_seat SET status = 'AVAILABLE' WHERE id = ?",
                seatMismatch.order().seatIds().get(1));
        assertNotRefundable(() -> refundApplicationService.requestRefund(command(
                seatMismatch.order().orderNo(),
                "seat-mismatch-request",
                null,
                "seat-mismatch-key")));

        assertThat(orderStatus(ticketMismatch.order().orderId())).isEqualTo("PAID");
        assertThat(orderStatus(seatMismatch.order().orderId())).isEqualTo("PAID");
        assertThat(countRows("refund_request")).isZero();
        assertThat(ticketStatus(ticketMismatch.order().orderId())).isEqualTo("INVALIDATED");
        assertThat(ticketStatus(seatMismatch.order().orderId())).isEqualTo("VALID");
        assertThat(seatStatus(seatMismatch.order().seatIds().getFirst())).isEqualTo("SOLD");
    }

    @Test
    void givenAnotherUser_whenQueryImpactRefundOrAlternatives_thenReturnNotFound() {
        PaidOrder paidOrder = createPaidOrder("refund-owner", 1);
        currentUserAccessor.useUser(USER_B);

        assertNotFound(() -> refundApplicationService.queryImpact(paidOrder.order().orderNo()));
        assertNotFound(() -> refundApplicationService.queryRefund(paidOrder.order().orderNo()));
        assertNotFound(() -> refundApplicationService.queryAlternativeShows(
                paidOrder.order().orderNo(),
                LocalDate.of(2026, 8, 2),
                LocalDate.of(2026, 8, 8)));
    }

    @Test
    void givenSamePaidOrder_whenTwoRefundsCompete_thenPersistOneSuccessfulRefund() throws Exception {
        PaidOrder paidOrder = createPaidOrder("refund-concurrent", 1);
        ExecutorService executor = Executors.newFixedThreadPool(2);
        CountDownLatch start = new CountDownLatch(1);
        try {
            Future<RefundView> first = executor.submit(() -> refundAfterSignal(
                    start,
                    paidOrder.order().orderNo(),
                    "concurrent-refund-request-1",
                    "concurrent-refund-key-1"));
            Future<RefundView> second = executor.submit(() -> refundAfterSignal(
                    start,
                    paidOrder.order().orderNo(),
                    "concurrent-refund-request-2",
                    "concurrent-refund-key-2"));
            start.countDown();

            RefundView firstResult = first.get(10, TimeUnit.SECONDS);
            RefundView secondResult = second.get(10, TimeUnit.SECONDS);
            assertThat(secondResult.refundNo()).isEqualTo(firstResult.refundNo());
        } finally {
            executor.shutdownNow();
            assertThat(executor.awaitTermination(5, TimeUnit.SECONDS)).isTrue();
        }
        assertThat(countRows("refund_request")).isEqualTo(1);
        assertThat(orderStatus(paidOrder.order().orderId())).isEqualTo("REFUNDED");
        assertThat(ticketStatus(paidOrder.order().orderId())).isEqualTo("REFUNDED");
        assertThat(seatStatus(paidOrder.order().seatIds().getFirst())).isEqualTo("AVAILABLE");
    }

    @Test
    void givenOriginalOrder_whenQueryAlternatives_thenReturnOnlyOtherFutureShowsOfSameMovie() {
        PaidOrder paidOrder = createPaidOrder("refund-alternatives", 1);
        long originalMovieId = movieId(paidOrder.order().showId());

        AlternativeShowsView result = refundApplicationService.queryAlternativeShows(
                paidOrder.order().orderNo(),
                LocalDate.of(2026, 8, 2),
                LocalDate.of(2026, 8, 8));

        assertThat(result.shows()).isNotEmpty();
        assertThat(result.shows()).allSatisfy(show -> {
            assertThat(show.showId()).isNotEqualTo(paidOrder.order().showId());
            assertThat(show.status()).isEqualTo("ON_SALE");
            assertThat(show.startTime()).isAfter(FIXED_LOCAL_TIME);
            assertThat(movieId(show.showId())).isEqualTo(originalMovieId);
        });
    }

    private PaidOrder createPaidOrder(String requestPrefix, int seatCount) {
        ShowSeats showSeats = findFutureShowSeats(seatCount);
        OrderView order = orderApplicationService.createOrder(new CreateOrderCommand(
                showSeats.showId(),
                showSeats.seatIds(),
                requestPrefix + "-create-request",
                requestPrefix + "-create-key"));
        PaymentView payment = paymentApplicationService.pay(order.orderNo(), requestPrefix + "-payment-key");
        return new PaidOrder(order, payment);
    }

    private RefundCommand command(
            String orderNo,
            String clientRequestId,
            String reason,
            String idempotencyKey) {
        return new RefundCommand(orderNo, reason, clientRequestId, null, idempotencyKey);
    }

    private RefundView refundAfterSignal(
            CountDownLatch start,
            String orderNo,
            String clientRequestId,
            String idempotencyKey) throws InterruptedException {
        start.await();
        currentUserAccessor.useUser(USER_A);
        try {
            return refundApplicationService.requestRefund(command(
                    orderNo,
                    clientRequestId,
                    null,
                    idempotencyKey));
        } finally {
            currentUserAccessor.clear();
        }
    }

    private ShowSeats findFutureShowSeats(int seatCount) {
        long showId = jdbcTemplate.queryForObject("""
                SELECT id
                  FROM movie_show
                 WHERE status = 'ON_SALE'
                   AND start_time > '2026-08-02 08:00:00'
                 ORDER BY start_time, id
                 LIMIT 1
                """, Long.class);
        List<Long> seatIds = jdbcTemplate.queryForList("""
                SELECT id
                  FROM show_seat
                 WHERE show_id = ?
                   AND status = 'AVAILABLE'
                 ORDER BY id
                 LIMIT ?
                """, Long.class, showId, seatCount);
        return new ShowSeats(showId, seatIds);
    }

    private void assertMismatch(org.assertj.core.api.ThrowableAssert.ThrowingCallable callable) {
        assertThatThrownBy(callable)
                .isInstanceOfSatisfying(BusinessException.class, exception -> assertThat(exception.getErrorCode())
                        .isEqualTo(OrderErrorCode.IDEMPOTENCY_PARAMETER_MISMATCH));
    }

    private void assertNotRefundable(org.assertj.core.api.ThrowableAssert.ThrowingCallable callable) {
        assertThatThrownBy(callable)
                .isInstanceOfSatisfying(BusinessException.class, exception -> assertThat(exception.getErrorCode())
                        .isEqualTo(OrderErrorCode.ORDER_NOT_REFUNDABLE));
    }

    private void assertNotFound(org.assertj.core.api.ThrowableAssert.ThrowingCallable callable) {
        assertThatThrownBy(callable)
                .isInstanceOfSatisfying(BusinessException.class, exception -> assertThat(exception.getErrorCode())
                        .isEqualTo(OrderErrorCode.ORDER_NOT_FOUND));
    }

    private long countRows(String tableName) {
        String sql = switch (tableName) {
            case "refund_request" -> "SELECT COUNT(*) FROM refund_request";
            default -> throw new IllegalArgumentException("未允许的测试表名");
        };
        Long count = jdbcTemplate.queryForObject(sql, Long.class);
        return count == null ? 0 : count;
    }

    private String orderStatus(long orderId) {
        return jdbcTemplate.queryForObject(
                "SELECT status FROM ticket_order WHERE id = ?",
                String.class,
                orderId);
    }

    private String ticketStatus(long orderId) {
        return jdbcTemplate.queryForObject(
                "SELECT status FROM electronic_ticket WHERE order_id = ?",
                String.class,
                orderId);
    }

    private String seatStatus(long seatId) {
        return jdbcTemplate.queryForObject(
                "SELECT status FROM show_seat WHERE id = ?",
                String.class,
                seatId);
    }

    private long movieId(long showId) {
        return jdbcTemplate.queryForObject(
                "SELECT movie_id FROM movie_show WHERE id = ?",
                Long.class,
                showId);
    }

    private record ShowSeats(long showId, List<Long> seatIds) {
    }

    private record PaidOrder(OrderView order, PaymentView payment) {
    }

    @TestConfiguration(proxyBeanMethods = false)
    static class RefundTestConfiguration {

        @Bean
        @Primary
        Clock fixedBusinessClock() {
            return Clock.fixed(FIXED_INSTANT, ZoneId.of("Asia/Shanghai"));
        }

        @Bean
        @Primary
        RefundCurrentUserAccessor refundCurrentUserAccessor() {
            return new RefundCurrentUserAccessor();
        }
    }

    static final class RefundCurrentUserAccessor implements CurrentUserAccessor {

        private final ThreadLocal<Long> currentUserId = ThreadLocal.withInitial(() -> USER_A);

        void useUser(long userId) {
            currentUserId.set(userId);
        }

        void clear() {
            currentUserId.remove();
        }

        @Override
        public CurrentUser requireCurrentUser() {
            return new CurrentUser(currentUserId.get(), RoleCode.USER, 0L);
        }
    }
}
