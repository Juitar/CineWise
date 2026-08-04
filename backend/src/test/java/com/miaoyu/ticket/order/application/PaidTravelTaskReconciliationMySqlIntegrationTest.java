package com.miaoyu.ticket.order.application;

import static org.assertj.core.api.Assertions.assertThat;

import com.miaoyu.ticket.auth.application.CurrentUser;
import com.miaoyu.ticket.auth.application.CurrentUserAccessor;
import com.miaoyu.ticket.auth.application.RoleCode;
import com.miaoyu.ticket.common.config.ClockConfiguration;
import java.time.Clock;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;
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

/** 使用A的隔离MySQL 8.4库验证paid_time与id键集分页及D任务唯一性。 */
@EnabledIfEnvironmentVariable(named = "CINEWISE_MYSQL_PAID_TRAVEL_RECONCILIATION_IT", matches = "true")
@ActiveProfiles("dev")
@SpringBootTest(properties = {
    "cinewise.seed.enabled=true",
    "cinewise.seed.fixed-value=20260802",
    "spring.flyway.enabled=true",
    "cinewise.transaction.expiry-job-enabled=false",
    "cinewise.transaction.paid-travel-reconciliation.enabled=false",
    "cinewise.transaction.paid-travel-reconciliation.batch-size=1",
    "management.health.redis.enabled=false"
})
@Import(PaidTravelTaskReconciliationMySqlIntegrationTest.MySqlTestConfiguration.class)
class PaidTravelTaskReconciliationMySqlIntegrationTest {

    private static final String REQUIRED_DATABASE = "cinewise_ticketing_concurrency_check";
    private static final long TEST_USER_ID = 9_900_001L;

    @Autowired
    private OrderApplicationService orderApplicationService;

    @Autowired
    private PaymentApplicationService paymentApplicationService;

    @Autowired
    private PaidTravelTaskReconciliationService reconciliationService;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private Clock clock;

    @BeforeEach
    void requireDedicatedDatabaseAndResetData() {
        assertThat(jdbcTemplate.queryForObject("SELECT DATABASE()", String.class))
                .as("PAID出行补偿MySQL测试只允许操作A的隔离库")
                .isEqualTo(REQUIRED_DATABASE);
        resetData();
    }

    @AfterEach
    void cleanFixtures() {
        resetData();
    }

    @Test
    void givenSameMillisecondPaidOrdersAndBatchOne_whenReconcile_thenEnsureBothUniqueTasks() {
        assertThat(jdbcTemplate.queryForObject("SELECT VERSION()", String.class)).startsWith("8.4.");
        ShowSeats fixture = findFutureShowSeats(2);
        PaidOrder first = createPaidOrder(fixture.showId(), fixture.seatIds().get(0), "mysql-paid-travel-first");
        PaidOrder second = createPaidOrder(fixture.showId(), fixture.seatIds().get(1), "mysql-paid-travel-second");
        jdbcTemplate.update("DELETE FROM travel_task WHERE order_id IN (?, ?)", first.orderId(), second.orderId());
        LocalDateTime sharedPaidAt = LocalDateTime.ofInstant(
                        clock.instant(),
                        ClockConfiguration.BUSINESS_ZONE_ID)
                .minusMinutes(1)
                .truncatedTo(ChronoUnit.MILLIS);
        jdbcTemplate.update(
                "UPDATE ticket_order SET paid_time = ? WHERE id IN (?, ?)",
                sharedPaidAt,
                first.orderId(),
                second.orderId());

        PaidTravelTaskReconciliationReport report = reconciliationService.reconcilePaidOrders();
        PaidTravelTaskReconciliationReport repeated = reconciliationService.reconcilePaidOrders();

        assertThat(report).isEqualTo(new PaidTravelTaskReconciliationReport(2, 2, 2, 0, 0));
        assertThat(repeated).isEqualTo(new PaidTravelTaskReconciliationReport(2, 2, 2, 0, 0));
        assertThat(countTravelTasks(first.orderId(), second.orderId())).isEqualTo(2);
        assertThat(countDistinctTravelOrders(first.orderId(), second.orderId())).isEqualTo(2);
        assertThat(countPayments(first.orderId(), second.orderId())).isEqualTo(2);
        assertThat(countTickets(first.orderId(), second.orderId())).isEqualTo(2);
    }

    private PaidOrder createPaidOrder(long showId, long seatId, String prefix) {
        OrderView order = orderApplicationService.createOrder(new CreateOrderCommand(
                showId,
                List.of(seatId),
                prefix + "-request",
                prefix + "-create-key"));
        paymentApplicationService.pay(order.orderNo(), prefix + "-payment-key");
        return new PaidOrder(order.orderId());
    }

    private ShowSeats findFutureShowSeats(int seatCount) {
        long showId = jdbcTemplate.queryForObject("""
                SELECT id
                  FROM movie_show
                 WHERE status = 'ON_SALE'
                   AND start_time > CURRENT_TIMESTAMP(3)
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

    private long countTravelTasks(long firstOrderId, long secondOrderId) {
        return count("SELECT COUNT(*) FROM travel_task WHERE order_id IN (?, ?)", firstOrderId, secondOrderId);
    }

    private long countDistinctTravelOrders(long firstOrderId, long secondOrderId) {
        return count(
                "SELECT COUNT(DISTINCT order_id) FROM travel_task WHERE order_id IN (?, ?)",
                firstOrderId,
                secondOrderId);
    }

    private long countPayments(long firstOrderId, long secondOrderId) {
        return count("SELECT COUNT(*) FROM mock_payment WHERE order_id IN (?, ?)", firstOrderId, secondOrderId);
    }

    private long countTickets(long firstOrderId, long secondOrderId) {
        return count("SELECT COUNT(*) FROM electronic_ticket WHERE order_id IN (?, ?)", firstOrderId, secondOrderId);
    }

    private long count(String sql, long firstOrderId, long secondOrderId) {
        Long count = jdbcTemplate.queryForObject(sql, Long.class, firstOrderId, secondOrderId);
        return count == null ? 0L : count;
    }

    private void resetData() {
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

    private record PaidOrder(long orderId) {
    }

    private record ShowSeats(long showId, List<Long> seatIds) {
    }

    @TestConfiguration(proxyBeanMethods = false)
    static class MySqlTestConfiguration {

        @Bean
        @Primary
        CurrentUserAccessor fixedCurrentUserAccessor() {
            return () -> new CurrentUser(TEST_USER_ID, RoleCode.USER, 0L);
        }
    }
}
