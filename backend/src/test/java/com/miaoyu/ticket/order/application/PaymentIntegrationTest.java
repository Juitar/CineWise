package com.miaoyu.ticket.order.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.miaoyu.ticket.auth.application.CurrentUser;
import com.miaoyu.ticket.auth.application.CurrentUserAccessor;
import com.miaoyu.ticket.auth.application.RoleCode;
import com.miaoyu.ticket.common.error.BusinessException;
import com.miaoyu.ticket.order.domain.ElectronicTicketStatus;
import com.miaoyu.ticket.order.domain.OrderStatus;
import com.miaoyu.ticket.order.domain.PaymentStatus;
import java.time.Clock;
import java.time.Instant;
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
@Import(PaymentIntegrationTest.PaymentTestConfiguration.class)
class PaymentIntegrationTest {

    private static final Instant FIXED_INSTANT = Instant.parse("2026-08-02T00:00:00Z");
    private static final LocalDateTime FIXED_LOCAL_TIME = LocalDateTime.of(2026, 8, 2, 8, 0);
    private static final long USER_A = 9_400_001L;
    private static final long USER_B = 9_400_002L;

    @Autowired
    private OrderApplicationService orderApplicationService;

    @Autowired
    private PaymentApplicationService paymentApplicationService;

    @Autowired
    private ElectronicTicketQueryService ticketQueryService;

    @Autowired
    private OrderExpiryTransaction orderExpiryTransaction;

    @Autowired
    private OrderCancellationService orderCancellationService;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private PaymentCurrentUserAccessor currentUserAccessor;

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
    void givenPayableOrder_whenPayAndReplay_thenReturnOnePaymentAndOneTicket() {
        OrderView order = createOrder("payment-success", "payment-create-key", 2);

        PaymentView paid = paymentApplicationService.pay(order.orderNo(), "payment-key");
        PaymentView repeatedWithOriginalKey = paymentApplicationService.pay(order.orderNo(), "payment-key");
        PaymentView repeatedWithNewKey = paymentApplicationService.pay(order.orderNo(), "payment-new-key");
        PaymentView recovered = paymentApplicationService.queryPayment(order.orderNo());
        ElectronicTicketView ticket = ticketQueryService.queryTicket(paid.ticketId());

        assertThat(paid.orderStatus()).isEqualTo(OrderStatus.PAID);
        assertThat(paid.paymentStatus()).isEqualTo(PaymentStatus.SUCCESS);
        assertThat(repeatedWithOriginalKey.paymentNo()).isEqualTo(paid.paymentNo());
        assertThat(repeatedWithNewKey.paymentNo()).isEqualTo(paid.paymentNo());
        assertThat(recovered).isEqualTo(paid);
        assertThat(ticket.status()).isEqualTo(ElectronicTicketStatus.VALID);
        assertThat(ticket.orderId()).isEqualTo(order.orderId());
        assertThat(ticket.seatIds()).containsExactlyElementsOf(order.seatIds());
        assertThat(ticket.qrPayload()).isEqualTo("cinewise:ticket:" + ticket.ticketCode());
        assertThat(countRows("mock_payment")).isEqualTo(1);
        assertThat(countRows("electronic_ticket")).isEqualTo(1);
        assertThat(order.seatIds()).allSatisfy(seatId -> assertThat(seatStatus(seatId)).isEqualTo("SOLD"));
        assertThat(countOrderSeats(order.orderNo(), "LOCKED")).isZero();
    }

    @Test
    void givenPaymentKeyBoundToFirstOrder_whenPaySecondOrder_thenRejectParameterMismatch() {
        OrderView first = createOrder("payment-first", "payment-first-create-key", 1);
        OrderView second = createOrder("payment-second", "payment-second-create-key", 1);
        paymentApplicationService.pay(first.orderNo(), "shared-payment-key");

        assertThatThrownBy(() -> paymentApplicationService.pay(second.orderNo(), "shared-payment-key"))
                .isInstanceOfSatisfying(BusinessException.class, exception -> assertThat(exception.getErrorCode())
                        .isEqualTo(OrderErrorCode.IDEMPOTENCY_PARAMETER_MISMATCH));
        assertThat(orderStatus(second.orderId())).isEqualTo("PENDING_PAYMENT");
        assertThat(countRows("mock_payment")).isEqualTo(1);
        assertThat(countRows("electronic_ticket")).isEqualTo(1);
    }

    @Test
    void givenExpiredOrder_whenPay_thenRejectWithoutPartialWrites() {
        OrderView order = createOrder("payment-expired", "payment-expired-create-key", 1);
        jdbcTemplate.update(
                "UPDATE ticket_order SET expire_time = ? WHERE id = ?",
                FIXED_LOCAL_TIME,
                order.orderId());

        assertThatThrownBy(() -> paymentApplicationService.pay(order.orderNo(), "expired-payment-key"))
                .isInstanceOfSatisfying(BusinessException.class, exception -> assertThat(exception.getErrorCode())
                        .isEqualTo(OrderErrorCode.ORDER_EXPIRED));
        assertThat(orderStatus(order.orderId())).isEqualTo("PENDING_PAYMENT");
        assertThat(countRows("mock_payment")).isZero();
        assertThat(countRows("electronic_ticket")).isZero();
        assertThat(countOrderSeats(order.orderNo(), "LOCKED")).isEqualTo(1);
    }

    @Test
    void givenSeatOwnershipChanged_whenPay_thenRollbackOrderPaymentAndTicket() {
        OrderView order = createOrder("payment-ownership", "payment-ownership-create-key", 2);
        jdbcTemplate.update(
                "UPDATE show_seat SET lock_order_no = 'OTHER-ORDER' WHERE id = ?",
                order.seatIds().get(1));

        assertThatThrownBy(() -> paymentApplicationService.pay(order.orderNo(), "ownership-payment-key"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("座位归属不完整");
        assertThat(orderStatus(order.orderId())).isEqualTo("PENDING_PAYMENT");
        assertThat(countRows("mock_payment")).isZero();
        assertThat(countRows("electronic_ticket")).isZero();
        assertThat(seatStatus(order.seatIds().getFirst())).isEqualTo("LOCKED");
        assertThat(seatLockOrder(order.seatIds().getFirst())).isEqualTo(order.orderNo());
        assertThat(seatLockOrder(order.seatIds().get(1))).isEqualTo("OTHER-ORDER");
    }

    @Test
    void givenAnotherUser_whenQueryPaymentOrTicket_thenReturnNotFound() {
        OrderView order = createOrder("payment-owner", "payment-owner-create-key", 1);
        PaymentView paid = paymentApplicationService.pay(order.orderNo(), "owner-payment-key");

        currentUserAccessor.useUser(USER_B);
        assertThatThrownBy(() -> paymentApplicationService.queryPayment(order.orderNo()))
                .isInstanceOfSatisfying(BusinessException.class, exception -> assertThat(exception.getErrorCode())
                        .isEqualTo(OrderErrorCode.ORDER_NOT_FOUND));
        assertThatThrownBy(() -> ticketQueryService.queryTicket(paid.ticketId()))
                .isInstanceOfSatisfying(BusinessException.class, exception -> assertThat(exception.getErrorCode())
                        .isEqualTo(OrderErrorCode.ORDER_NOT_FOUND));
    }

    @Test
    void givenSameOrder_whenTwoPaymentsCompete_thenReturnOneCommittedResult() throws Exception {
        OrderView order = createOrder("payment-concurrent", "payment-concurrent-create-key", 1);
        ExecutorService executor = Executors.newFixedThreadPool(2);
        CountDownLatch start = new CountDownLatch(1);
        try {
            Future<PaymentView> first = executor.submit(() -> payAfterSignal(
                    start,
                    order.orderNo(),
                    "concurrent-payment-key-1"));
            Future<PaymentView> second = executor.submit(() -> payAfterSignal(
                    start,
                    order.orderNo(),
                    "concurrent-payment-key-2"));
            start.countDown();

            PaymentView firstResult = first.get(10, TimeUnit.SECONDS);
            PaymentView secondResult = second.get(10, TimeUnit.SECONDS);
            assertThat(secondResult.paymentNo()).isEqualTo(firstResult.paymentNo());
            assertThat(secondResult.ticketId()).isEqualTo(firstResult.ticketId());
        } finally {
            executor.shutdownNow();
            assertThat(executor.awaitTermination(5, TimeUnit.SECONDS)).isTrue();
        }
        assertThat(countRows("mock_payment")).isEqualTo(1);
        assertThat(countRows("electronic_ticket")).isEqualTo(1);
        assertThat(orderStatus(order.orderId())).isEqualTo("PAID");
    }

    @Test
    void givenOrderNearExpiry_whenPaymentAndExpiryCompete_thenCommitOneLegalTerminalState() throws Exception {
        OrderView order = createOrder("payment-expiry", "payment-expiry-create-key", 1);
        LocalDateTime expiryBoundary = FIXED_LOCAL_TIME.plusMinutes(1);
        jdbcTemplate.update(
                "UPDATE ticket_order SET expire_time = ? WHERE id = ?",
                expiryBoundary,
                order.orderId());
        ExecutorService executor = Executors.newFixedThreadPool(2);
        CountDownLatch start = new CountDownLatch(1);
        try {
            Future<String> payment = executor.submit(() -> paymentStateAfterSignal(start, order.orderNo()));
            Future<String> expiry = executor.submit(() -> expiryStateAfterSignal(
                    start,
                    order.orderId(),
                    expiryBoundary));
            start.countDown();
            assertThat(List.of(payment.get(10, TimeUnit.SECONDS), expiry.get(10, TimeUnit.SECONDS)))
                    .containsExactlyInAnyOrder("PAYMENT_FINISHED", "EXPIRY_FINISHED");
        } finally {
            executor.shutdownNow();
            assertThat(executor.awaitTermination(5, TimeUnit.SECONDS)).isTrue();
        }

        String finalStatus = orderStatus(order.orderId());
        assertThat(finalStatus).isIn("PAID", "EXPIRED");
        if ("PAID".equals(finalStatus)) {
            assertThat(countRows("mock_payment")).isEqualTo(1);
            assertThat(countRows("electronic_ticket")).isEqualTo(1);
            assertThat(seatStatus(order.seatIds().getFirst())).isEqualTo("SOLD");
        } else {
            assertThat(countRows("mock_payment")).isZero();
            assertThat(countRows("electronic_ticket")).isZero();
            assertThat(seatStatus(order.seatIds().getFirst())).isEqualTo("AVAILABLE");
        }
    }

    @Test
    void givenPendingOrder_whenPaymentAndCancellationCompete_thenCommitOneLegalTerminalState() throws Exception {
        OrderView order = createOrder("payment-cancel", "payment-cancel-create-key", 1);
        ExecutorService executor = Executors.newFixedThreadPool(2);
        CountDownLatch start = new CountDownLatch(1);
        try {
            Future<String> payment = executor.submit(() -> paymentStateAfterSignal(start, order.orderNo()));
            Future<String> cancellation = executor.submit(() -> cancellationStateAfterSignal(start, order.orderNo()));
            start.countDown();
            assertThat(List.of(payment.get(10, TimeUnit.SECONDS), cancellation.get(10, TimeUnit.SECONDS)))
                    .containsExactlyInAnyOrder("PAYMENT_FINISHED", "CANCELLATION_FINISHED");
        } finally {
            executor.shutdownNow();
            assertThat(executor.awaitTermination(5, TimeUnit.SECONDS)).isTrue();
        }

        String finalStatus = orderStatus(order.orderId());
        assertThat(finalStatus).isIn("PAID", "CANCELLED");
        assertThat(seatStatus(order.seatIds().getFirst()))
                .isEqualTo("PAID".equals(finalStatus) ? "SOLD" : "AVAILABLE");
        assertThat(countRows("mock_payment")).isEqualTo("PAID".equals(finalStatus) ? 1 : 0);
        assertThat(countRows("electronic_ticket")).isEqualTo("PAID".equals(finalStatus) ? 1 : 0);
    }

    private OrderView createOrder(String requestId, String idempotencyKey, int seatCount) {
        ShowSeats showSeats = findFutureShowSeats(seatCount);
        return orderApplicationService.createOrder(new CreateOrderCommand(
                showSeats.showId(),
                showSeats.seatIds(),
                requestId,
                idempotencyKey));
    }

    private PaymentView payAfterSignal(
            CountDownLatch start,
            String orderNo,
            String idempotencyKey) throws InterruptedException {
        start.await();
        currentUserAccessor.useUser(USER_A);
        try {
            return paymentApplicationService.pay(orderNo, idempotencyKey);
        } finally {
            currentUserAccessor.clear();
        }
    }

    private String paymentStateAfterSignal(CountDownLatch start, String orderNo) throws InterruptedException {
        start.await();
        currentUserAccessor.useUser(USER_A);
        try {
            paymentApplicationService.pay(orderNo, "race-payment-key");
        } catch (BusinessException exception) {
            assertThat(exception.getErrorCode())
                    .isIn(OrderErrorCode.ORDER_STATE_CONFLICT, OrderErrorCode.ORDER_EXPIRED);
        } finally {
            currentUserAccessor.clear();
        }
        return "PAYMENT_FINISHED";
    }

    private String expiryStateAfterSignal(
            CountDownLatch start,
            long orderId,
            LocalDateTime expiryBoundary) throws InterruptedException {
        start.await();
        orderExpiryTransaction.expire(orderId, expiryBoundary);
        return "EXPIRY_FINISHED";
    }

    private String cancellationStateAfterSignal(CountDownLatch start, String orderNo) throws InterruptedException {
        start.await();
        currentUserAccessor.useUser(USER_A);
        try {
            orderCancellationService.cancelOrder(orderNo, "race-cancel-key");
        } catch (BusinessException exception) {
            assertThat(exception.getErrorCode()).isEqualTo(OrderErrorCode.ORDER_STATE_CONFLICT);
        } finally {
            currentUserAccessor.clear();
        }
        return "CANCELLATION_FINISHED";
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

    private long countRows(String tableName) {
        String sql = switch (tableName) {
            case "mock_payment" -> "SELECT COUNT(*) FROM mock_payment";
            case "electronic_ticket" -> "SELECT COUNT(*) FROM electronic_ticket";
            default -> throw new IllegalArgumentException("未允许的测试表名");
        };
        Long count = jdbcTemplate.queryForObject(sql, Long.class);
        return count == null ? 0 : count;
    }

    private long countOrderSeats(String orderNo, String status) {
        Long count = jdbcTemplate.queryForObject("""
                SELECT COUNT(*)
                  FROM show_seat
                 WHERE lock_order_no = ?
                   AND status = ?
                """, Long.class, orderNo, status);
        return count == null ? 0 : count;
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

    private record ShowSeats(long showId, List<Long> seatIds) {
    }

    @TestConfiguration(proxyBeanMethods = false)
    static class PaymentTestConfiguration {

        @Bean
        @Primary
        Clock fixedBusinessClock() {
            return Clock.fixed(FIXED_INSTANT, ZoneId.of("Asia/Shanghai"));
        }

        @Bean
        @Primary
        PaymentCurrentUserAccessor paymentCurrentUserAccessor() {
            return new PaymentCurrentUserAccessor();
        }
    }

    static final class PaymentCurrentUserAccessor implements CurrentUserAccessor {

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
