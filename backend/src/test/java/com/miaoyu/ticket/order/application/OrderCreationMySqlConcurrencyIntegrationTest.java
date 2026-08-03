package com.miaoyu.ticket.order.application;

import static org.assertj.core.api.Assertions.assertThat;

import com.miaoyu.ticket.auth.application.CurrentUser;
import com.miaoyu.ticket.auth.application.CurrentUserAccessor;
import com.miaoyu.ticket.auth.application.RoleCode;
import com.miaoyu.ticket.common.error.BusinessException;
import com.miaoyu.ticket.ticketing.application.TicketingErrorCode;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
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

/** 使用独立本地MySQL库验证InnoDB条件更新的真实防超卖行为。 */
@EnabledIfEnvironmentVariable(named = "CINEWISE_MYSQL_CONCURRENCY_IT", matches = "true")
@ActiveProfiles("dev")
@SpringBootTest(properties = {
    "cinewise.seed.enabled=true",
    "cinewise.seed.fixed-value=20260802",
    "spring.flyway.enabled=true",
    "management.health.redis.enabled=false"
})
@Import(OrderCreationMySqlConcurrencyIntegrationTest.MySqlOrderTestConfiguration.class)
class OrderCreationMySqlConcurrencyIntegrationTest {

    private static final String REQUIRED_DATABASE = "cinewise_ticketing_concurrency_check";
    private static final int COMPETITOR_COUNT = 20;
    private static final long BASE_USER_ID = 9_100_000L;

    @Autowired
    private OrderApplicationService orderApplicationService;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private MySqlCurrentUserAccessor currentUserAccessor;

    @BeforeEach
    void requireDedicatedDatabaseAndResetTransactions() {
        assertThat(jdbcTemplate.queryForObject("SELECT DATABASE()", String.class))
                .as("并发测试只允许操作独立临时库")
                .isEqualTo(REQUIRED_DATABASE);
        resetTransactionsAndSeats();
    }

    @AfterEach
    void cleanTransactionFixtures() {
        resetTransactionsAndSeats();
    }

    @Test
    void givenMySqlInnoDb_whenTwentyUsersCompeteForOneSeat_thenOnlyOneOrderCommits() throws Exception {
        String mysqlVersion = jdbcTemplate.queryForObject("SELECT VERSION()", String.class);
        assertThat(mysqlVersion).startsWith("8.4.");
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

        List<OrderAttempt> attempts = executeConcurrentRequests(showId, seatId);
        List<OrderView> successfulOrders = attempts.stream()
                .map(OrderAttempt::order)
                .filter(Objects::nonNull)
                .toList();
        assertThat(successfulOrders).hasSize(1);
        assertThat(attempts.stream()
                .map(OrderAttempt::errorCode)
                .filter(Objects::nonNull)
                .toList()).containsOnly(TicketingErrorCode.SEAT_NOT_LOCKABLE);
        assertThat(countOrders()).isEqualTo(1);
        assertThat(countOrderSeats()).isEqualTo(1);
        assertThat(jdbcTemplate.queryForObject(
                "SELECT status FROM show_seat WHERE id = ?",
                String.class,
                seatId)).isEqualTo("LOCKED");
        assertThat(jdbcTemplate.queryForObject(
                "SELECT lock_order_no FROM show_seat WHERE id = ?",
                String.class,
                seatId)).isEqualTo(successfulOrders.getFirst().orderNo());
    }

    private List<OrderAttempt> executeConcurrentRequests(long showId, long seatId) throws Exception {
        CountDownLatch ready = new CountDownLatch(COMPETITOR_COUNT);
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(COMPETITOR_COUNT);
        List<Future<OrderAttempt>> futures = new ArrayList<>();
        try {
            for (int index = 0; index < COMPETITOR_COUNT; index++) {
                int requestIndex = index;
                futures.add(executor.submit(() -> competeForSeat(
                        ready,
                        start,
                        showId,
                        seatId,
                        requestIndex)));
            }
            assertThat(ready.await(5, TimeUnit.SECONDS)).isTrue();
            start.countDown();
            List<OrderAttempt> attempts = new ArrayList<>();
            for (Future<OrderAttempt> future : futures) {
                attempts.add(future.get(30, TimeUnit.SECONDS));
            }
            return attempts;
        } finally {
            executor.shutdownNow();
            assertThat(executor.awaitTermination(5, TimeUnit.SECONDS)).isTrue();
        }
    }

    private OrderAttempt competeForSeat(
            CountDownLatch ready,
            CountDownLatch start,
            long showId,
            long seatId,
            int requestIndex) throws InterruptedException {
        currentUserAccessor.useUser(BASE_USER_ID + requestIndex);
        ready.countDown();
        start.await();
        try {
            OrderView order = orderApplicationService.createOrder(new CreateOrderCommand(
                    showId,
                    List.of(seatId),
                    "mysql-concurrent-request-" + requestIndex,
                    "mysql-concurrent-key-" + requestIndex));
            return new OrderAttempt(order, null);
        } catch (BusinessException exception) {
            return new OrderAttempt(null, exception.getErrorCode());
        } finally {
            currentUserAccessor.clear();
        }
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

    private long countOrders() {
        Long count = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM ticket_order", Long.class);
        return count == null ? 0 : count;
    }

    private long countOrderSeats() {
        Long count = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM ticket_order_seat", Long.class);
        return count == null ? 0 : count;
    }

    private record OrderAttempt(
            OrderView order,
            com.miaoyu.ticket.common.error.ErrorCode errorCode) {
    }

    @TestConfiguration(proxyBeanMethods = false)
    static class MySqlOrderTestConfiguration {

        @Bean
        @Primary
        MySqlCurrentUserAccessor mySqlCurrentUserAccessor() {
            return new MySqlCurrentUserAccessor();
        }
    }

    static final class MySqlCurrentUserAccessor implements CurrentUserAccessor {

        private final ThreadLocal<Long> currentUserId = ThreadLocal.withInitial(() -> BASE_USER_ID);

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
