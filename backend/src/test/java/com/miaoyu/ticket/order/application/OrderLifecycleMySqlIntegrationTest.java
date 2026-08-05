package com.miaoyu.ticket.order.application;

import static org.assertj.core.api.Assertions.assertThat;

import com.miaoyu.ticket.auth.application.CurrentUser;
import com.miaoyu.ticket.auth.application.CurrentUserAccessor;
import com.miaoyu.ticket.auth.application.RoleCode;
import com.miaoyu.ticket.common.config.ClockConfiguration;
import com.miaoyu.ticket.order.domain.OrderStatus;
import java.time.Clock;
import java.time.LocalDateTime;
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

/** 使用A的独立本地MySQL库验证V005、取消幂等和过期条件更新。 */
@EnabledIfEnvironmentVariable(named = "CINEWISE_MYSQL_LIFECYCLE_IT", matches = "true")
@ActiveProfiles("dev")
@SpringBootTest(properties = {
    "cinewise.seed.enabled=true",
    "cinewise.seed.fixed-value=20260802",
    "spring.flyway.enabled=true",
    "cinewise.transaction.expiry-job-enabled=false",
    "management.health.redis.enabled=false"
})
@Import(OrderLifecycleMySqlIntegrationTest.MySqlLifecycleTestConfiguration.class)
class OrderLifecycleMySqlIntegrationTest {

    private static final String REQUIRED_DATABASE = "cinewise_ticketing_concurrency_check";
    private static final long TEST_USER_ID = 9_300_001L;

    @Autowired
    private OrderApplicationService orderApplicationService;

    @Autowired
    private OrderCancellationService orderCancellationService;

    @Autowired
    private OrderQueryService orderQueryService;

    @Autowired
    private OrderExpiryTransaction orderExpiryTransaction;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private Clock clock;

    @BeforeEach
    void requireDedicatedDatabaseAndResetTransactions() {
        assertThat(jdbcTemplate.queryForObject("SELECT DATABASE()", String.class))
                .as("订单生命周期MySQL测试只允许操作A的独立临时库")
                .isEqualTo(REQUIRED_DATABASE);
        resetTransactionsAndSeats();
    }

    @AfterEach
    void cleanTransactionFixtures() {
        resetTransactionsAndSeats();
    }

    @Test
    void givenMySqlEight_whenCancelAndExpireOrders_thenPersistOneAuthoritativeResult() {
        assertThat(jdbcTemplate.queryForObject("SELECT VERSION()", String.class)).startsWith("8.4.");
        assertThat(jdbcTemplate.queryForObject("""
                SELECT COUNT(*)
                  FROM flyway_schema_history
                 WHERE version = '005'
                   AND success = 1
                """, Integer.class)).isEqualTo(1);
        assertThat(jdbcTemplate.queryForObject("""
                SELECT table_collation
                  FROM information_schema.tables
                 WHERE table_schema = DATABASE()
                   AND table_name = 'ticket_order_operation'
                """, String.class)).isEqualTo("utf8mb4_0900_ai_ci");

        ShowSeats fixture = findFutureShowSeats(2);
        OrderView cancellable = createOrder(
                fixture.showId(),
                List.of(fixture.seatIds().get(0)),
                "mysql-lifecycle-cancel-request",
                "mysql-lifecycle-cancel-create-key");
        OrderQueryView orderDetail = orderQueryService.queryOrder(cancellable.orderNo());
        assertThat(orderDetail.movieId()).isEqualTo(fixture.movieId());
        assertThat(orderDetail.cinemaId()).isEqualTo(fixture.cinemaId());
        assertThat(orderDetail.showStartTime()).isEqualTo(fixture.showStartTime());
        assertThat(orderQueryService.queryOrders(new OrderListQuery(null, null, null, null, 1, 10))
                .records().getFirst().showStartTime()).isEqualTo(fixture.showStartTime());
        OrderView cancelled = orderCancellationService.cancelOrder(
                cancellable.orderNo(),
                "mysql-lifecycle-cancel-key");
        OrderView repeated = orderCancellationService.cancelOrder(
                cancellable.orderNo(),
                "mysql-lifecycle-cancel-key");
        assertThat(cancelled.status()).isEqualTo(OrderStatus.CANCELLED);
        assertThat(repeated.stateVersion()).isEqualTo(cancelled.stateVersion());
        assertThat(countOperations()).isEqualTo(1);
        assertThat(seatStatus(fixture.seatIds().get(0))).isEqualTo("AVAILABLE");

        OrderView expiring = createOrder(
                fixture.showId(),
                List.of(fixture.seatIds().get(1)),
                "mysql-lifecycle-expiry-request",
                "mysql-lifecycle-expiry-create-key");
        LocalDateTime now = LocalDateTime.ofInstant(
                clock.instant(),
                ClockConfiguration.BUSINESS_ZONE_ID);
        // MySQL DATETIME(3) 会截断 Java 纳秒；真实库集成用例使用明确已过期值，等号边界由固定时钟单元测试覆盖。
        jdbcTemplate.update(
                "UPDATE ticket_order SET expire_time = ? WHERE id = ?",
                now.minusSeconds(1),
                expiring.orderId());
        assertThat(orderExpiryTransaction.expire(expiring.orderId(), now)).isTrue();
        assertThat(orderExpiryTransaction.expire(expiring.orderId(), now)).isFalse();
        assertThat(orderStatus(expiring.orderId())).isEqualTo("EXPIRED");
        assertThat(seatStatus(fixture.seatIds().get(1))).isEqualTo("AVAILABLE");
    }

    private OrderView createOrder(long showId, List<Long> seatIds, String requestId, String idempotencyKey) {
        return orderApplicationService.createOrder(new CreateOrderCommand(
                showId,
                seatIds,
                requestId,
                idempotencyKey));
    }

    private ShowSeats findFutureShowSeats(int seatCount) {
        ShowContext show = jdbcTemplate.queryForObject("""
                SELECT id,
                       movie_id,
                       cinema_id,
                       start_time
                  FROM movie_show
                 WHERE status = 'ON_SALE'
                   AND start_time > CURRENT_TIMESTAMP(3)
                 ORDER BY start_time, id
                 LIMIT 1
                """, (resultSet, rowNumber) -> new ShowContext(
                resultSet.getLong("id"),
                resultSet.getLong("movie_id"),
                resultSet.getLong("cinema_id"),
                resultSet.getTimestamp("start_time").toLocalDateTime()));
        List<Long> seatIds = jdbcTemplate.queryForList("""
                SELECT id
                  FROM show_seat
                 WHERE show_id = ?
                   AND status = 'AVAILABLE'
                 ORDER BY id
                 LIMIT ?
                """, Long.class, show.showId(), seatCount);
        return new ShowSeats(
                show.showId(),
                show.movieId(),
                show.cinemaId(),
                show.showStartTime(),
                seatIds);
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

    private long countOperations() {
        Long count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM ticket_order_operation",
                Long.class);
        return count == null ? 0 : count;
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

    private record ShowSeats(
            long showId,
            long movieId,
            long cinemaId,
            LocalDateTime showStartTime,
            List<Long> seatIds) {
    }

    private record ShowContext(
            long showId,
            long movieId,
            long cinemaId,
            LocalDateTime showStartTime) {
    }

    @TestConfiguration(proxyBeanMethods = false)
    static class MySqlLifecycleTestConfiguration {

        @Bean
        @Primary
        CurrentUserAccessor fixedCurrentUserAccessor() {
            return () -> new CurrentUser(TEST_USER_ID, RoleCode.USER, 0L);
        }
    }
}
