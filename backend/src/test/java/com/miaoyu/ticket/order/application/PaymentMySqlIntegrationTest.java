package com.miaoyu.ticket.order.application;

import static org.assertj.core.api.Assertions.assertThat;

import com.miaoyu.ticket.auth.application.CurrentUser;
import com.miaoyu.ticket.auth.application.CurrentUserAccessor;
import com.miaoyu.ticket.auth.application.RoleCode;
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

/** 使用A的独立本地MySQL库验证支付行锁、唯一支付和唯一出票。 */
@EnabledIfEnvironmentVariable(named = "CINEWISE_MYSQL_PAYMENT_IT", matches = "true")
@ActiveProfiles("dev")
@SpringBootTest(properties = {
    "cinewise.seed.enabled=true",
    "cinewise.seed.fixed-value=20260802",
    "spring.flyway.enabled=true",
    "cinewise.transaction.expiry-job-enabled=false",
    "management.health.redis.enabled=false"
})
@Import(PaymentMySqlIntegrationTest.MySqlPaymentTestConfiguration.class)
class PaymentMySqlIntegrationTest {

    private static final String REQUIRED_DATABASE = "cinewise_ticketing_concurrency_check";
    private static final long TEST_USER_ID = 9_500_001L;
    private static final Instant FIXED_INSTANT = Instant.parse("2026-08-02T00:00:00Z");
    private static final LocalDateTime FIXED_LOCAL_TIME = LocalDateTime.of(2026, 8, 2, 8, 0);

    @Autowired
    private OrderApplicationService orderApplicationService;

    @Autowired
    private PaymentApplicationService paymentApplicationService;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @BeforeEach
    void requireDedicatedDatabaseAndResetTransactions() {
        assertThat(jdbcTemplate.queryForObject("SELECT DATABASE()", String.class))
                .as("支付MySQL测试只允许操作A的独立临时库")
                .isEqualTo(REQUIRED_DATABASE);
        resetTransactionsAndSeats();
    }

    @AfterEach
    void cleanTransactionFixtures() {
        resetTransactionsAndSeats();
    }

    @Test
    void givenMySqlEight_whenTwoPaymentsCompete_thenPersistOnePaymentAndTicket() throws Exception {
        assertThat(jdbcTemplate.queryForObject("SELECT VERSION()", String.class)).startsWith("8.4.");
        ShowSeat fixture = findFutureShowSeat();
        OrderView order = orderApplicationService.createOrder(new CreateOrderCommand(
                fixture.showId(),
                List.of(fixture.seatId()),
                "mysql-payment-create-request",
                "mysql-payment-create-key"));

        ExecutorService executor = Executors.newFixedThreadPool(2);
        CountDownLatch start = new CountDownLatch(1);
        try {
            Future<PaymentView> first = executor.submit(() -> payAfterSignal(
                    start,
                    order.orderNo(),
                    "mysql-payment-key-1"));
            Future<PaymentView> second = executor.submit(() -> payAfterSignal(
                    start,
                    order.orderNo(),
                    "mysql-payment-key-2"));
            start.countDown();

            PaymentView firstResult = first.get(15, TimeUnit.SECONDS);
            PaymentView secondResult = second.get(15, TimeUnit.SECONDS);
            assertThat(secondResult.paymentNo()).isEqualTo(firstResult.paymentNo());
            assertThat(secondResult.ticketId()).isEqualTo(firstResult.ticketId());
        } finally {
            executor.shutdownNow();
            assertThat(executor.awaitTermination(5, TimeUnit.SECONDS)).isTrue();
        }

        assertThat(count("mock_payment")).isEqualTo(1);
        assertThat(count("electronic_ticket")).isEqualTo(1);
        assertThat(orderStatus(order.orderId())).isEqualTo("PAID");
        assertThat(seatStatus(fixture.seatId())).isEqualTo("SOLD");
        assertThat(paymentApplicationService.queryPayment(order.orderNo()).ticketId()).isNotNull();
    }

    private PaymentView payAfterSignal(
            CountDownLatch start,
            String orderNo,
            String idempotencyKey) throws InterruptedException {
        start.await();
        return paymentApplicationService.pay(orderNo, idempotencyKey);
    }

    private ShowSeat findFutureShowSeat() {
        long showId = jdbcTemplate.queryForObject("""
                SELECT id
                  FROM movie_show
                 WHERE status = 'ON_SALE'
                   AND start_time > ?
                 ORDER BY start_time, id
                 LIMIT 1
                """, Long.class, FIXED_LOCAL_TIME);
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
            case "mock_payment" -> "SELECT COUNT(*) FROM mock_payment";
            case "electronic_ticket" -> "SELECT COUNT(*) FROM electronic_ticket";
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
    static class MySqlPaymentTestConfiguration {

        @Bean
        @Primary
        CurrentUserAccessor fixedCurrentUserAccessor() {
            return () -> new CurrentUser(TEST_USER_ID, RoleCode.USER, 0L);
        }

        /** 与固定种子窗口使用相同业务时间，测试不得依赖 MySQL 或机器当前日期。 */
        @Bean
        @Primary
        Clock fixedBusinessClock() {
            return Clock.fixed(FIXED_INSTANT, ZoneId.of("Asia/Shanghai"));
        }
    }
}
