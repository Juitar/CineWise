package com.miaoyu.ticket.order.application;

import static org.assertj.core.api.Assertions.assertThat;

import com.miaoyu.ticket.auth.application.CurrentUser;
import com.miaoyu.ticket.auth.application.CurrentUserAccessor;
import com.miaoyu.ticket.auth.application.RoleCode;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

/** 使用A的本地隔离MySQL库验证退款行锁、JSON快照和唯一退款。 */
@EnabledIfEnvironmentVariable(named = "CINEWISE_MYSQL_REFUND_IT", matches = "true")
@ActiveProfiles("dev")
@SpringBootTest(properties = {
    "cinewise.seed.enabled=true",
    "cinewise.seed.fixed-value=20260802",
    "spring.flyway.enabled=true",
    "cinewise.transaction.expiry-job-enabled=false",
    "management.health.redis.enabled=false"
})
@Import(RefundMySqlIntegrationTest.MySqlRefundTestConfiguration.class)
class RefundMySqlIntegrationTest {

    private static final String REQUIRED_DATABASE = "cinewise_ticketing_concurrency_check";
    private static final long TEST_USER_ID = 9_700_001L;

    @Autowired
    private OrderApplicationService orderApplicationService;

    @Autowired
    private PaymentApplicationService paymentApplicationService;

    @Autowired
    private RefundApplicationService refundApplicationService;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @BeforeEach
    void requireDedicatedDatabaseAndResetTransactions() {
        assertThat(jdbcTemplate.queryForObject("SELECT DATABASE()", String.class))
                .as("退款MySQL测试只允许操作A的本地隔离库")
                .isEqualTo(REQUIRED_DATABASE);
        resetTransactionsAndSeats();
    }

    @AfterEach
    void cleanTransactionFixtures() {
        resetTransactionsAndSeats();
    }

    @Test
    void givenMySqlEight_whenTwoRefundsCompete_thenPersistOneRefundAndReleaseSeat() throws Exception {
        assertThat(jdbcTemplate.queryForObject("SELECT VERSION()", String.class)).startsWith("8.4.");
        ShowSeat fixture = findFutureShowSeat();
        OrderView order = orderApplicationService.createOrder(new CreateOrderCommand(
                fixture.showId(),
                List.of(fixture.seatId()),
                "mysql-refund-create-request",
                "mysql-refund-create-key"));
        paymentApplicationService.pay(order.orderNo(), "mysql-refund-payment-key");

        ExecutorService executor = Executors.newFixedThreadPool(2);
        CountDownLatch start = new CountDownLatch(1);
        try {
            Future<RefundView> first = executor.submit(() -> refundAfterSignal(
                    start,
                    order.orderNo(),
                    "mysql-refund-request-1",
                    "mysql-refund-key-1"));
            Future<RefundView> second = executor.submit(() -> refundAfterSignal(
                    start,
                    order.orderNo(),
                    "mysql-refund-request-2",
                    "mysql-refund-key-2"));
            start.countDown();

            RefundView firstResult = first.get(15, TimeUnit.SECONDS);
            RefundView secondResult = second.get(15, TimeUnit.SECONDS);
            assertThat(secondResult.refundNo()).isEqualTo(firstResult.refundNo());
        } finally {
            executor.shutdownNow();
            assertThat(executor.awaitTermination(5, TimeUnit.SECONDS)).isTrue();
        }

        assertThat(count("refund_request")).isEqualTo(1);
        assertThat(orderStatus(order.orderId())).isEqualTo("REFUNDED");
        assertThat(ticketStatus(order.orderId())).isEqualTo("REFUNDED");
        assertThat(seatStatus(fixture.seatId())).isEqualTo("AVAILABLE");
        assertThat(refundApplicationService.queryRefund(order.orderNo()).refundStatus().name())
                .isEqualTo("SUCCESS");
    }

    private RefundView refundAfterSignal(
            CountDownLatch start,
            String orderNo,
            String clientRequestId,
            String idempotencyKey) throws InterruptedException {
        start.await();
        return refundApplicationService.requestRefund(new RefundCommand(
                orderNo,
                null,
                clientRequestId,
                null,
                idempotencyKey));
    }

    private ShowSeat findFutureShowSeat() {
        long showId = jdbcTemplate.queryForObject("""
                SELECT id
                  FROM movie_show
                 WHERE status = 'ON_SALE'
                   AND start_time > CURRENT_TIMESTAMP(3)
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
        return new ShowSeat(showId, seatId);
    }

    private long count(String tableName) {
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

    private void resetTransactionsAndSeats() {
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

    private record ShowSeat(long showId, long seatId) {
    }

    @TestConfiguration(proxyBeanMethods = false)
    static class MySqlRefundTestConfiguration {

        @Bean
        @Primary
        CurrentUserAccessor fixedCurrentUserAccessor() {
            return () -> new CurrentUser(TEST_USER_ID, RoleCode.USER, 0L);
        }
    }
}
