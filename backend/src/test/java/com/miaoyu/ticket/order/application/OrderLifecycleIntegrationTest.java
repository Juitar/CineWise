package com.miaoyu.ticket.order.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.miaoyu.ticket.auth.application.CurrentUser;
import com.miaoyu.ticket.auth.application.CurrentUserAccessor;
import com.miaoyu.ticket.auth.application.RoleCode;
import com.miaoyu.ticket.common.error.BusinessException;
import com.miaoyu.ticket.order.domain.OrderStatus;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Set;
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
@Import(OrderLifecycleIntegrationTest.LifecycleTestConfiguration.class)
class OrderLifecycleIntegrationTest {

    private static final Instant FIXED_INSTANT = Instant.parse("2026-08-02T00:00:00Z");
    private static final LocalDateTime FIXED_LOCAL_TIME = LocalDateTime.of(2026, 8, 2, 8, 0);
    private static final long USER_A = 9_200_001L;
    private static final long USER_B = 9_200_002L;

    @Autowired
    private OrderApplicationService orderApplicationService;

    @Autowired
    private OrderQueryService orderQueryService;

    @Autowired
    private OrderCancellationService orderCancellationService;

    @Autowired
    private OrderExpiryService orderExpiryService;

    @Autowired
    private OrderExpiryTransaction orderExpiryTransaction;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private LifecycleCurrentUserAccessor currentUserAccessor;

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
    void givenOwnOrders_whenQueryAndCancel_thenFilterOwnershipAndReturnIdempotentResult() {
        ShowSeats fixture = findFutureShowSeats(4);
        OrderView firstOrder = createOrder(
                fixture.showId(),
                fixture.seatIds().subList(0, 2),
                "lifecycle-create-1",
                "lifecycle-create-key-1");

        OrderPageView ownPage = orderQueryService.queryOrders(new OrderListQuery(
                null,
                "PENDING_PAYMENT",
                LocalDate.of(2026, 8, 2),
                LocalDate.of(2026, 8, 2),
                1,
                20));
        assertThat(ownPage.total()).isEqualTo(1);
        assertThat(ownPage.records().getFirst().orderId()).isEqualTo(firstOrder.orderId());
        assertThat(orderQueryService.queryOrder(firstOrder.orderNo()).seatIds())
                .containsExactlyElementsOf(firstOrder.seatIds());

        currentUserAccessor.useUser(USER_B);
        assertThat(orderQueryService.queryOrders(new OrderListQuery(null, null, null, null, 1, 20)).total())
                .isZero();
        assertThatThrownBy(() -> orderQueryService.queryOrder(firstOrder.orderNo()))
                .isInstanceOfSatisfying(BusinessException.class, exception -> assertThat(exception.getErrorCode())
                        .isEqualTo(OrderErrorCode.ORDER_NOT_FOUND));

        currentUserAccessor.useUser(USER_A);
        OrderView cancelled = orderCancellationService.cancelOrder(firstOrder.orderNo(), "cancel-key-1");
        OrderView repeated = orderCancellationService.cancelOrder(firstOrder.orderNo(), "cancel-key-1");
        assertThat(cancelled.status()).isEqualTo(OrderStatus.CANCELLED);
        assertThat(repeated.orderId()).isEqualTo(cancelled.orderId());
        assertThat(repeated.stateVersion()).isEqualTo(cancelled.stateVersion());
        assertThat(countOperations()).isEqualTo(1);
        assertThat(countSeats(firstOrder.orderNo(), "LOCKED")).isZero();

        OrderView secondOrder = createOrder(
                fixture.showId(),
                List.of(fixture.seatIds().get(2)),
                "lifecycle-create-2",
                "lifecycle-create-key-2");
        assertThatThrownBy(() -> orderCancellationService.cancelOrder(secondOrder.orderNo(), "cancel-key-1"))
                .isInstanceOfSatisfying(BusinessException.class, exception -> assertThat(exception.getErrorCode())
                        .isEqualTo(OrderErrorCode.IDEMPOTENCY_PARAMETER_MISMATCH));
        assertThat(orderStatus(secondOrder.orderId())).isEqualTo("PENDING_PAYMENT");
        assertThat(countSeats(secondOrder.orderNo(), "LOCKED")).isEqualTo(1);
    }

    @Test
    void givenSameCancellationKey_whenTwoRequestsCancelSameOrder_thenReturnOneCommittedResult() throws Exception {
        ShowSeats fixture = findFutureShowSeats(1);
        OrderView order = createOrder(
                fixture.showId(),
                fixture.seatIds(),
                "concurrent-cancel-create",
                "concurrent-cancel-create-key");

        ExecutorService executor = Executors.newFixedThreadPool(2);
        CountDownLatch start = new CountDownLatch(1);
        try {
            Future<OrderView> first = executor.submit(() -> cancelAfterSignal(
                    start,
                    order.orderNo(),
                    "concurrent-cancel-key"));
            Future<OrderView> second = executor.submit(() -> cancelAfterSignal(
                    start,
                    order.orderNo(),
                    "concurrent-cancel-key"));
            start.countDown();

            OrderView firstResult = first.get(10, TimeUnit.SECONDS);
            OrderView secondResult = second.get(10, TimeUnit.SECONDS);
            assertThat(firstResult.status()).isEqualTo(OrderStatus.CANCELLED);
            assertThat(secondResult.status()).isEqualTo(OrderStatus.CANCELLED);
            assertThat(secondResult.orderId()).isEqualTo(firstResult.orderId());
            assertThat(secondResult.stateVersion()).isEqualTo(firstResult.stateVersion());
        } finally {
            executor.shutdownNow();
            assertThat(executor.awaitTermination(5, TimeUnit.SECONDS)).isTrue();
        }

        assertThat(countOperations()).isEqualTo(1);
        assertThat(orderStatus(order.orderId())).isEqualTo("CANCELLED");
        assertThat(seatStatus(fixture.seatIds().getFirst())).isEqualTo("AVAILABLE");
    }

    @Test
    void givenSeatOwnershipChanged_whenCancel_thenRollbackOrderAndAnyPartialRelease() {
        ShowSeats fixture = findFutureShowSeats(2);
        OrderView order = createOrder(
                fixture.showId(),
                fixture.seatIds(),
                "ownership-create",
                "ownership-create-key");
        long changedSeatId = fixture.seatIds().get(1);
        jdbcTemplate.update(
                "UPDATE show_seat SET lock_order_no = 'OTHER-ORDER' WHERE id = ?",
                changedSeatId);

        assertThatThrownBy(() -> orderCancellationService.cancelOrder(order.orderNo(), "ownership-cancel-key"))
                .isInstanceOf(IllegalStateException.class);
        assertThat(orderStatus(order.orderId())).isEqualTo("PENDING_PAYMENT");
        assertThat(seatStatus(fixture.seatIds().getFirst())).isEqualTo("LOCKED");
        assertThat(seatLockOrder(fixture.seatIds().getFirst())).isEqualTo(order.orderNo());
        assertThat(seatStatus(changedSeatId)).isEqualTo("LOCKED");
        assertThat(seatLockOrder(changedSeatId)).isEqualTo("OTHER-ORDER");
        assertThat(countOperations()).isZero();
    }

    @Test
    void givenExpiredOrder_whenTwoExpiryTransactionsCompete_thenOnlyOneTransitionWins() throws Exception {
        ShowSeats fixture = findFutureShowSeats(1);
        OrderView order = createOrder(
                fixture.showId(),
                fixture.seatIds(),
                "expiry-create",
                "expiry-create-key");
        makeExpired(order.orderId());

        ExecutorService executor = Executors.newFixedThreadPool(2);
        CountDownLatch start = new CountDownLatch(1);
        try {
            Future<Boolean> first = executor.submit(() -> expireAfterSignal(start, order.orderId()));
            Future<Boolean> second = executor.submit(() -> expireAfterSignal(start, order.orderId()));
            start.countDown();
            assertThat(List.of(first.get(10, TimeUnit.SECONDS), second.get(10, TimeUnit.SECONDS)))
                    .containsExactlyInAnyOrder(true, false);
        } finally {
            executor.shutdownNow();
            assertThat(executor.awaitTermination(5, TimeUnit.SECONDS)).isTrue();
        }

        assertThat(orderStatus(order.orderId())).isEqualTo("EXPIRED");
        assertThat(seatStatus(fixture.seatIds().getFirst())).isEqualTo("AVAILABLE");
        assertThat(orderExpiryService.releaseExpiredOrders().scannedCount()).isZero();
    }

    @Test
    void givenOrderAtExpiryBoundary_whenCancelAndExpiryCompete_thenOnlyOneTerminalStateCommits() throws Exception {
        ShowSeats fixture = findFutureShowSeats(1);
        OrderView order = createOrder(
                fixture.showId(),
                fixture.seatIds(),
                "cancel-expiry-create",
                "cancel-expiry-create-key");
        makeExpired(order.orderId());

        ExecutorService executor = Executors.newFixedThreadPool(2);
        CountDownLatch start = new CountDownLatch(1);
        try {
            Future<String> cancellation = executor.submit(() -> cancelAfterSignal(start, order.orderNo()));
            Future<String> expiry = executor.submit(() -> expireStateAfterSignal(start, order.orderId()));
            start.countDown();
            assertThat(Set.of(cancellation.get(10, TimeUnit.SECONDS), expiry.get(10, TimeUnit.SECONDS)))
                    .contains("CANCELLED_OR_CONFLICT", "EXPIRED_OR_SKIPPED");
        } finally {
            executor.shutdownNow();
            assertThat(executor.awaitTermination(5, TimeUnit.SECONDS)).isTrue();
        }

        assertThat(orderStatus(order.orderId())).isIn("CANCELLED", "EXPIRED");
        assertThat(seatStatus(fixture.seatIds().getFirst())).isEqualTo("AVAILABLE");
        assertThat(countOperations()).isIn(0L, 1L);
    }

    @Test
    void givenPayingAndPaidOrders_whenExpiryServiceScans_thenLeaveOrdersAndSeatsUnchanged() {
        ShowSeats fixture = findFutureShowSeats(2);
        OrderView paying = createOrder(
                fixture.showId(),
                List.of(fixture.seatIds().get(0)),
                "paying-create",
                "paying-create-key");
        OrderView paid = createOrder(
                fixture.showId(),
                List.of(fixture.seatIds().get(1)),
                "paid-create",
                "paid-create-key");
        jdbcTemplate.update(
                "UPDATE ticket_order SET status = 'PAYING', expire_time = ? WHERE id = ?",
                FIXED_LOCAL_TIME.minusMinutes(1),
                paying.orderId());
        jdbcTemplate.update(
                "UPDATE ticket_order SET status = 'PAID', expire_time = ? WHERE id = ?",
                FIXED_LOCAL_TIME.minusMinutes(1),
                paid.orderId());
        jdbcTemplate.update(
                "UPDATE show_seat SET status = 'SOLD' WHERE lock_order_no = ?",
                paid.orderNo());

        OrderExpiryReport report = orderExpiryService.releaseExpiredOrders();

        assertThat(report.scannedCount()).isZero();
        assertThat(orderStatus(paying.orderId())).isEqualTo("PAYING");
        assertThat(orderStatus(paid.orderId())).isEqualTo("PAID");
        assertThat(seatStatus(fixture.seatIds().get(0))).isEqualTo("LOCKED");
        assertThat(seatStatus(fixture.seatIds().get(1))).isEqualTo("SOLD");
    }

    private OrderView createOrder(long showId, List<Long> seatIds, String requestId, String idempotencyKey) {
        return orderApplicationService.createOrder(new CreateOrderCommand(
                showId,
                seatIds,
                requestId,
                idempotencyKey));
    }

    private boolean expireAfterSignal(CountDownLatch start, long orderId) throws InterruptedException {
        start.await();
        return orderExpiryTransaction.expire(orderId, FIXED_LOCAL_TIME);
    }

    private String cancelAfterSignal(CountDownLatch start, String orderNo) throws InterruptedException {
        start.await();
        currentUserAccessor.useUser(USER_A);
        try {
            orderCancellationService.cancelOrder(orderNo, "cancel-expiry-key");
        } catch (BusinessException exception) {
            assertThat(exception.getErrorCode()).isEqualTo(OrderErrorCode.ORDER_STATE_CONFLICT);
        } finally {
            currentUserAccessor.clear();
        }
        return "CANCELLED_OR_CONFLICT";
    }

    private OrderView cancelAfterSignal(
            CountDownLatch start,
            String orderNo,
            String idempotencyKey) throws InterruptedException {
        start.await();
        currentUserAccessor.useUser(USER_A);
        try {
            return orderCancellationService.cancelOrder(orderNo, idempotencyKey);
        } finally {
            currentUserAccessor.clear();
        }
    }

    private String expireStateAfterSignal(CountDownLatch start, long orderId) throws InterruptedException {
        start.await();
        orderExpiryTransaction.expire(orderId, FIXED_LOCAL_TIME);
        return "EXPIRED_OR_SKIPPED";
    }

    private void makeExpired(long orderId) {
        jdbcTemplate.update(
                "UPDATE ticket_order SET expire_time = ? WHERE id = ?",
                FIXED_LOCAL_TIME,
                orderId);
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
                 ORDER BY id
                 LIMIT ?
                """, Long.class, showId, seatCount);
        return new ShowSeats(showId, seatIds);
    }

    private String orderStatus(long orderId) {
        return jdbcTemplate.queryForObject(
                "SELECT status FROM ticket_order WHERE id = ?",
                String.class,
                orderId);
    }

    private String seatStatus(long seatId) {
        return jdbcTemplate.queryForObject(
                "SELECT status FROM show_seat WHERE id = ?",
                String.class,
                seatId);
    }

    private String seatLockOrder(long seatId) {
        return jdbcTemplate.queryForObject(
                "SELECT lock_order_no FROM show_seat WHERE id = ?",
                String.class,
                seatId);
    }

    private long countOperations() {
        Long count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM ticket_order_operation",
                Long.class);
        return count == null ? 0 : count;
    }

    private long countSeats(String orderNo, String status) {
        Long count = jdbcTemplate.queryForObject("""
                SELECT COUNT(*)
                  FROM show_seat
                 WHERE lock_order_no = ?
                   AND status = ?
                """, Long.class, orderNo, status);
        return count == null ? 0 : count;
    }

    private record ShowSeats(long showId, List<Long> seatIds) {
    }

    @TestConfiguration(proxyBeanMethods = false)
    static class LifecycleTestConfiguration {

        @Bean
        @Primary
        Clock fixedBusinessClock() {
            return Clock.fixed(FIXED_INSTANT, ZoneId.of("Asia/Shanghai"));
        }

        @Bean
        @Primary
        LifecycleCurrentUserAccessor lifecycleCurrentUserAccessor() {
            return new LifecycleCurrentUserAccessor();
        }
    }

    static final class LifecycleCurrentUserAccessor implements CurrentUserAccessor {

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
