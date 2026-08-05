package com.miaoyu.ticket.order.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.miaoyu.ticket.auth.application.CurrentUser;
import com.miaoyu.ticket.auth.application.CurrentUserAccessor;
import com.miaoyu.ticket.auth.application.RoleCode;
import com.miaoyu.ticket.common.error.BusinessException;
import com.miaoyu.ticket.order.api.CreateOrderPrecheckCommand;
import com.miaoyu.ticket.order.api.CreateOrderTool;
import com.miaoyu.ticket.order.api.OrderPrecheckResult;
import com.miaoyu.ticket.order.domain.OrderStatus;
import com.miaoyu.ticket.ticketing.application.TicketingErrorCode;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.ArrayList;
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
@Import(OrderApplicationServiceIntegrationTest.OrderTestConfiguration.class)
class OrderApplicationServiceIntegrationTest {

    private static final Instant FIXED_INSTANT = Instant.parse("2026-08-02T00:00:00Z");
    private static final long DEFAULT_USER_ID = 9_000_001L;

    @Autowired
    private OrderApplicationService orderApplicationService;

    @Autowired
    private CreateOrderTool createOrderTool;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private TestCurrentUserAccessor currentUserAccessor;

    @BeforeEach
    void resetTransactionData() {
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
        currentUserAccessor.useUser(DEFAULT_USER_ID);
    }

    @Test
    void givenSameRequest_whenCreateAndRecover_thenReturnOriginalOrderAndRejectChangedParameters() {
        ShowSeats fixture = findFutureShowSeats(3);
        CreateOrderCommand command = new CreateOrderCommand(
                fixture.showId(),
                fixture.seatIds().subList(0, 2),
                "create-request-1",
                "create-order-1");

        OrderView created = orderApplicationService.createOrder(command);
        OrderView repeated = orderApplicationService.createOrder(command);
        OrderView recovered = orderApplicationService.queryByClientRequestId(command.clientRequestId());

        assertThat(created.orderId()).isEqualTo(repeated.orderId()).isEqualTo(recovered.orderId());
        assertThat(created.status()).isEqualTo(OrderStatus.PENDING_PAYMENT);
        assertThat(created.ticketCount()).isEqualTo(2);
        assertThat(created.totalAmount())
                .isEqualByComparingTo(created.unitPrice().multiply(java.math.BigDecimal.valueOf(2)));
        assertThat(count("ticket_order")).isEqualTo(1);
        assertThat(count("ticket_order_seat")).isEqualTo(2);
        assertThat(countLockedSeats(created.orderNo())).isEqualTo(2);

        CreateOrderCommand changedSeats = new CreateOrderCommand(
                fixture.showId(),
                List.of(fixture.seatIds().get(2)),
                command.clientRequestId(),
                command.idempotencyKey());
        assertThatThrownBy(() -> orderApplicationService.createOrder(changedSeats))
                .isInstanceOfSatisfying(BusinessException.class, exception -> assertThat(exception.getErrorCode())
                        .isEqualTo(OrderErrorCode.IDEMPOTENCY_PARAMETER_MISMATCH));
        assertThat(count("ticket_order")).isEqualTo(1);

        currentUserAccessor.useUser(DEFAULT_USER_ID + 1);
        assertThatThrownBy(() -> orderApplicationService.queryByClientRequestId(command.clientRequestId()))
                .isInstanceOfSatisfying(BusinessException.class, exception -> assertThat(exception.getErrorCode())
                        .isEqualTo(OrderErrorCode.ORDER_NOT_FOUND));
    }

    @Test
    void givenPrecheckSelection_whenValidateRuns_thenReadOnlyResultDoesNotCreateOrderOrLockSeat() {
        ShowSeats fixture = findFutureShowSeats(1);
        long seatId = fixture.seatIds().getFirst();

        OrderPrecheckResult result = createOrderTool.validate(new CreateOrderPrecheckCommand(
                Long.toString(fixture.showId()),
                List.of(Long.toString(seatId))));

        assertThat(result.executable()).isTrue();
        assertThat(result.errorCode()).isNull();
        assertThat(seatStatus(seatId)).isEqualTo("AVAILABLE");
        assertThat(count("ticket_order")).isZero();
        assertThat(count("ticket_order_seat")).isZero();
    }

    @Test
    void givenLaterSeatAlreadyLocked_whenCreateMultipleSeats_thenRollbackEarlierSeatAndOrder() {
        ShowSeats fixture = findFutureShowSeats(2);
        long firstSeatId = fixture.seatIds().get(0);
        long conflictingSeatId = fixture.seatIds().get(1);
        jdbcTemplate.update("""
                UPDATE show_seat
                   SET status = 'LOCKED',
                       lock_order_no = 'EXISTING-ORDER',
                       lock_expire_time = '2026-08-02 09:00:00',
                       version = version + 1
                 WHERE id = ?
                """, conflictingSeatId);

        CreateOrderCommand command = new CreateOrderCommand(
                fixture.showId(),
                fixture.seatIds(),
                "partial-conflict-request",
                "partial-conflict-key");
        assertThatThrownBy(() -> orderApplicationService.createOrder(command))
                .isInstanceOfSatisfying(BusinessException.class, exception -> assertThat(exception.getErrorCode())
                        .isEqualTo(TicketingErrorCode.SEAT_NOT_LOCKABLE));

        assertThat(seatStatus(firstSeatId)).isEqualTo("AVAILABLE");
        assertThat(seatLockOrder(firstSeatId)).isNull();
        assertThat(seatStatus(conflictingSeatId)).isEqualTo("LOCKED");
        assertThat(seatLockOrder(conflictingSeatId)).isEqualTo("EXISTING-ORDER");
        assertThat(count("ticket_order")).isZero();
        assertThat(count("ticket_order_seat")).isZero();
    }

    @Test
    void givenTwentyRequests_whenCompeteForSameSeat_thenAtMostOneOrderSucceeds() throws Exception {
        ShowSeats fixture = findFutureShowSeats(1);
        long seatId = fixture.seatIds().getFirst();
        int competitorCount = 20;
        CountDownLatch ready = new CountDownLatch(competitorCount);
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(competitorCount);
        List<Future<OrderAttempt>> futures = new ArrayList<>();
        try {
            for (int index = 0; index < competitorCount; index++) {
                long userId = DEFAULT_USER_ID + index;
                String suffix = Integer.toString(index);
                futures.add(executor.submit(() -> competeForSeat(
                        ready,
                        start,
                        userId,
                        fixture.showId(),
                        seatId,
                        suffix)));
            }
            assertThat(ready.await(5, TimeUnit.SECONDS)).isTrue();
            start.countDown();

            List<OrderAttempt> attempts = new ArrayList<>();
            for (Future<OrderAttempt> future : futures) {
                attempts.add(future.get(20, TimeUnit.SECONDS));
            }
            List<OrderView> successfulOrders = attempts.stream()
                    .map(OrderAttempt::order)
                    .filter(java.util.Objects::nonNull)
                    .toList();
            assertThat(successfulOrders).hasSize(1);
            assertThat(attempts.stream()
                    .filter(attempt -> attempt.errorCode() != null)
                    .map(OrderAttempt::errorCode)
                    .toList())
                    .containsOnly(TicketingErrorCode.SEAT_NOT_LOCKABLE);
            assertThat(count("ticket_order")).isEqualTo(1);
            assertThat(count("ticket_order_seat")).isEqualTo(1);
            assertThat(seatStatus(seatId)).isEqualTo("LOCKED");
            assertThat(seatLockOrder(seatId)).isEqualTo(successfulOrders.getFirst().orderNo());
        } finally {
            executor.shutdownNow();
            assertThat(executor.awaitTermination(5, TimeUnit.SECONDS)).isTrue();
        }
    }

    private OrderAttempt competeForSeat(
            CountDownLatch ready,
            CountDownLatch start,
            long userId,
            long showId,
            long seatId,
            String suffix) throws InterruptedException {
        currentUserAccessor.useUser(userId);
        ready.countDown();
        start.await();
        try {
            OrderView order = orderApplicationService.createOrder(new CreateOrderCommand(
                    showId,
                    List.of(seatId),
                    "concurrent-request-" + suffix,
                    "concurrent-key-" + suffix));
            return new OrderAttempt(order, null);
        } catch (BusinessException exception) {
            return new OrderAttempt(null, exception.getErrorCode());
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
                 ORDER BY id
                 LIMIT ?
                """, Long.class, showId, seatCount);
        return new ShowSeats(showId, seatIds);
    }

    private long count(String tableName) {
        String sql = switch (tableName) {
            case "ticket_order" -> "SELECT COUNT(*) FROM ticket_order";
            case "ticket_order_seat" -> "SELECT COUNT(*) FROM ticket_order_seat";
            default -> throw new IllegalArgumentException("未允许的测试表名");
        };
        Long value = jdbcTemplate.queryForObject(sql, Long.class);
        return value == null ? 0 : value;
    }

    private long countLockedSeats(String orderNo) {
        Long value = jdbcTemplate.queryForObject("""
                SELECT COUNT(*)
                  FROM show_seat
                 WHERE status = 'LOCKED'
                   AND lock_order_no = ?
                """, Long.class, orderNo);
        return value == null ? 0 : value;
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

    private record ShowSeats(long showId, List<Long> seatIds) {
    }

    private record OrderAttempt(OrderView order, com.miaoyu.ticket.common.error.ErrorCode errorCode) {
    }

    @TestConfiguration(proxyBeanMethods = false)
    static class OrderTestConfiguration {

        @Bean
        @Primary
        Clock fixedBusinessClock() {
            return Clock.fixed(FIXED_INSTANT, ZoneId.of("Asia/Shanghai"));
        }

        @Bean
        @Primary
        TestCurrentUserAccessor testCurrentUserAccessor() {
            return new TestCurrentUserAccessor();
        }
    }

    static final class TestCurrentUserAccessor implements CurrentUserAccessor {

        private final ThreadLocal<Long> currentUserId = ThreadLocal.withInitial(() -> DEFAULT_USER_ID);

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
