package com.miaoyu.ticket.order.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.miaoyu.ticket.auth.application.CurrentUser;
import com.miaoyu.ticket.auth.application.CurrentUserAccessor;
import com.miaoyu.ticket.auth.application.RoleCode;
import com.miaoyu.ticket.common.error.BusinessException;
import com.miaoyu.ticket.content.application.ContentSummaryQueryPort;
import com.miaoyu.ticket.order.domain.ElectronicTicketStatus;
import com.miaoyu.ticket.order.domain.OrderStatus;
import com.miaoyu.ticket.order.domain.RefundStatus;
import com.miaoyu.ticket.order.event.OrderInvalidated;
import com.miaoyu.ticket.ticketing.application.ShowContextQueryService;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.context.event.EventListener;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

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

    @Autowired
    private OrderInvalidatedProbe orderInvalidatedProbe;

    @Autowired
    private RefundTravelEventContextResolver travelEventContextResolver;

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
        orderInvalidatedProbe.reset();
        travelEventContextResolver.reset();
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
        assertThat(orderInvalidatedProbe.events()).singleElement().satisfies(event -> {
            assertThat(event.eventId()).isNotBlank();
            assertThat(event.orderId()).isEqualTo(Long.toString(paidOrder.order().orderId()));
            assertThat(event.showId()).isEqualTo(Long.toString(paidOrder.order().showId()));
            assertThat(event.cinemaId()).isEqualTo(Long.toString(cinemaId(paidOrder.order().showId())));
            assertThat(event.cinemaId()).matches("[1-9][0-9]*");
            assertThat(event.userId()).isEqualTo(Long.toString(USER_A));
            assertThat(event.cinemaArea()).isNotBlank();
            assertThat(event.startAt().getOffset()).isEqualTo(ZoneOffset.ofHours(8));
            assertThat(event.orderVersion()).isEqualTo(refunded.stateVersion());
            assertThat(event.occurredAt()).isEqualTo(OffsetDateTime.of(
                    FIXED_LOCAL_TIME,
                    ZoneOffset.ofHours(8)));
            assertThat(event.invalidReason()).isEqualTo("REFUNDED");
        });
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
        assertThat(orderInvalidatedProbe.events()).isEmpty();
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
        assertThat(orderInvalidatedProbe.events()).hasSize(1);
    }

    @Test
    void givenExpiredCinemaSummary_whenRefund_thenCommitWithoutFabricatingEvent() {
        PaidOrder paidOrder = createPaidOrder("refund-expired-area", 1);
        long cinemaId = cinemaId(paidOrder.order().showId());
        Timestamp originalExpiresAt = jdbcTemplate.queryForObject(
                "SELECT expires_at FROM cinema WHERE id = ?",
                Timestamp.class,
                cinemaId);
        try {
            jdbcTemplate.update(
                    "UPDATE cinema SET expires_at = ? WHERE id = ?",
                    Timestamp.valueOf(FIXED_LOCAL_TIME.minusSeconds(1)),
                    cinemaId);

            RefundView refunded = refundApplicationService.requestRefund(command(
                    paidOrder.order().orderNo(),
                    "refund-expired-area-request",
                    null,
                    "refund-expired-area-key"));

            assertThat(refunded.orderStatus()).isEqualTo(OrderStatus.REFUNDED);
            assertThat(orderInvalidatedProbe.events()).isEmpty();
        } finally {
            jdbcTemplate.update("UPDATE cinema SET expires_at = ? WHERE id = ?", originalExpiresAt, cinemaId);
        }
    }

    @Test
    void givenContextQueryFails_whenRefund_thenCommitAndWaitForReconciliation() {
        PaidOrder paidOrder = createPaidOrder("refund-context-failure", 1);
        travelEventContextResolver.rejectNextResolve();

        RefundView refunded = refundApplicationService.requestRefund(command(
                paidOrder.order().orderNo(),
                "refund-context-failure-request",
                null,
                "refund-context-failure-key"));

        assertThat(refunded.orderStatus()).isEqualTo(OrderStatus.REFUNDED);
        assertThat(refundApplicationService.queryRefund(paidOrder.order().orderNo())).isEqualTo(refunded);
        assertThat(orderInvalidatedProbe.events()).isEmpty();
    }

    @Test
    void givenSpringPublisherRejectsRegistration_whenRefund_thenRefundStillCommits() {
        PaidOrder paidOrder = createPaidOrder("refund-publisher-failure", 1);
        orderInvalidatedProbe.rejectNextRegistration();

        RefundView refunded = refundApplicationService.requestRefund(command(
                paidOrder.order().orderNo(),
                "refund-publisher-failure-request",
                null,
                "refund-publisher-failure-key"));

        assertThat(refunded.orderStatus()).isEqualTo(OrderStatus.REFUNDED);
        assertThat(refundApplicationService.queryRefund(paidOrder.order().orderNo())).isEqualTo(refunded);
        assertThat(orderInvalidatedProbe.events()).isEmpty();
    }

    @Test
    void givenAfterCommitConsumerFails_whenRefund_thenReturnAndRecoverRefundedOrder() {
        PaidOrder paidOrder = createPaidOrder("refund-consumer-failure", 1);
        orderInvalidatedProbe.failNextAfterCommit();

        RefundView refunded = refundApplicationService.requestRefund(command(
                paidOrder.order().orderNo(),
                "refund-consumer-failure-request",
                null,
                "refund-consumer-failure-key"));

        RefundView recovered = refundApplicationService.queryRefund(paidOrder.order().orderNo());
        assertThat(refunded.orderStatus()).isEqualTo(OrderStatus.REFUNDED);
        assertThat(recovered).isEqualTo(refunded);
        assertThat(orderInvalidatedProbe.events()).isEmpty();
    }

    @Test
    void givenOriginalOrder_whenQueryAlternatives_thenReturnOnlyOtherFutureShowsOfSameMovieAndCinema() {
        PaidOrder paidOrder = createPaidOrder("refund-alternatives", 1);
        long originalMovieId = movieId(paidOrder.order().showId());
        long originalCinemaId = cinemaId(paidOrder.order().showId());
        insertAlternativeShow(paidOrder.order().showId(), originalMovieId, originalCinemaId);

        AlternativeShowsView result = refundApplicationService.queryAlternativeShows(
                paidOrder.order().orderNo(),
                LocalDate.of(2026, 8, 2),
                LocalDate.of(2026, 8, 8));

        assertThat(result.shows()).isNotEmpty();
        assertThat(result.shows()).allSatisfy(show -> {
            assertThat(show.showId()).isNotEqualTo(paidOrder.order().showId());
            assertThat(show.movieId()).isEqualTo(originalMovieId);
            assertThat(show.cinemaId()).isEqualTo(originalCinemaId);
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

    /** 替补场次属于退款查询的独立契约，测试不依赖演示种子恰好分配到相同影片。 */
    private void insertAlternativeShow(long originalShowId, long movieId, long cinemaId) {
        long auditoriumId = jdbcTemplate.queryForObject(
                "SELECT auditorium_id FROM movie_show WHERE id = ?", Long.class, originalShowId);
        jdbcTemplate.update("""
                INSERT INTO movie_show (
                    id, movie_id, cinema_id, auditorium_id, start_time, end_time,
                    language_version, base_price, data_type, source, status, version, create_time, update_time
                ) VALUES (?, ?, ?, ?, '2026-08-03 14:00:00', '2026-08-03 16:00:00',
                    '国语 2D', 39.90, 'MOCK', 'refund-test', 'ON_SALE', 0,
                    '2026-08-02 08:00:00', '2026-08-02 08:00:00')
                """, 9_700_000_001L, movieId, cinemaId, auditoriumId);
        jdbcTemplate.update("""
                INSERT INTO show_seat (
                    id, show_id, row_no, seat_no, seat_label, status,
                    lock_order_no, lock_expire_time, version, create_time, update_time
                ) VALUES (?, ?, 'A', '01', 'A排1座', 'AVAILABLE', NULL, NULL, 0,
                    '2026-08-02 08:00:00', '2026-08-02 08:00:00')
                """, 9_700_000_002L, 9_700_000_001L);
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

    private long cinemaId(long showId) {
        return jdbcTemplate.queryForObject(
                "SELECT cinema_id FROM movie_show WHERE id = ?",
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

        @Bean
        OrderInvalidatedProbe orderInvalidatedProbe() {
            return new OrderInvalidatedProbe();
        }

        @Bean
        @Primary
        RefundTravelEventContextResolver refundTravelEventContextResolver(
                ShowContextQueryService showContextQueryService,
                ContentSummaryQueryPort contentSummaryQueryPort) {
            return new RefundTravelEventContextResolver(showContextQueryService, contentSummaryQueryPort);
        }
    }

    /** 同时验证Spring同步登记失败隔离和AFTER_COMMIT成功消费时机。 */
    static final class OrderInvalidatedProbe {

        private final List<OrderInvalidated> events = new CopyOnWriteArrayList<>();
        private final AtomicBoolean rejectNextRegistration = new AtomicBoolean();
        private final AtomicBoolean failNextAfterCommit = new AtomicBoolean();

        @Order(Ordered.HIGHEST_PRECEDENCE)
        @EventListener
        public void rejectRegistrationWhenRequested(OrderInvalidated event) {
            if (rejectNextRegistration.compareAndSet(true, false)) {
                throw new IllegalStateException("测试退款事件登记失败");
            }
        }

        /** 只有退款事务成功提交后才把事件加入测试探针。 */
        @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
        public void captureAfterCommit(OrderInvalidated event) {
            if (failNextAfterCommit.compareAndSet(true, false)) {
                throw new IllegalStateException("测试退款AFTER_COMMIT消费者失败");
            }
            events.add(event);
        }

        List<OrderInvalidated> events() {
            return List.copyOf(events);
        }

        void rejectNextRegistration() {
            rejectNextRegistration.set(true);
        }

        void failNextAfterCommit() {
            failNextAfterCommit.set(true);
        }

        void reset() {
            events.clear();
            rejectNextRegistration.set(false);
            failNextAfterCommit.set(false);
        }
    }

    /** 可控故障仅用于证明D公开摘要异常不会进入退款事务。 */
    static final class RefundTravelEventContextResolver extends TravelEventContextResolver {

        private final AtomicBoolean rejectNextResolve = new AtomicBoolean();

        RefundTravelEventContextResolver(
                ShowContextQueryService showContextQueryService,
                ContentSummaryQueryPort contentSummaryQueryPort) {
            super(showContextQueryService, contentSummaryQueryPort);
        }

        @Override
        public Optional<TravelEventContext> resolve(OrderRepository.OrderSnapshot order) {
            if (rejectNextResolve.compareAndSet(true, false)) {
                throw new IllegalStateException("测试出行上下文查询失败");
            }
            return super.resolve(order);
        }

        void rejectNextResolve() {
            rejectNextResolve.set(true);
        }

        void reset() {
            rejectNextResolve.set(false);
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
