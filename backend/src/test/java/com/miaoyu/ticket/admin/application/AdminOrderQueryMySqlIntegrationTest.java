package com.miaoyu.ticket.admin.application;

import static org.assertj.core.api.Assertions.assertThat;

import com.miaoyu.ticket.auth.application.CurrentUser;
import com.miaoyu.ticket.auth.application.CurrentUserAccessor;
import com.miaoyu.ticket.auth.application.RoleCode;
import com.miaoyu.ticket.auth.application.UserAdminQueryPort;
import com.miaoyu.ticket.order.domain.ElectronicTicketStatus;
import com.miaoyu.ticket.order.domain.OrderStatus;
import com.miaoyu.ticket.order.domain.PaymentStatus;
import com.miaoyu.ticket.order.domain.RefundStatus;
import java.math.BigDecimal;
import java.net.URI;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.ApplicationContextInitializer;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.ContextConfiguration;

/**
 * 使用A的本地隔离MySQL库验证管理订单只读查询。
 *
 * <p>用例直接经过Application Service和真实MyBatis XML，因此可以发现H2无法暴露的
 * MySQL枚举、DECIMAL、DATETIME(3)、动态IN和LIMIT/OFFSET映射问题。</p>
 *
 * <p>上下文刷新前先校验最终JDBC URL的主机和库名，阻止DataSource或Flyway连接错误目标。
 * 测试只使用预留的高位ID写入夹具，每个用例前后按ID精确清理。</p>
 */
@EnabledIfEnvironmentVariable(named = "CINEWISE_MYSQL_ADMIN_ORDER_IT", matches = "true")
@ActiveProfiles("dev")
@SpringBootTest(properties = {
    "cinewise.seed.enabled=false",
    "spring.flyway.enabled=true",
    "cinewise.transaction.expiry-job-enabled=false",
    "management.health.redis.enabled=false",
    "cinewise.auth.jwt-secret=mysql-admin-it-jwt-secret-at-least-32-bytes",
    "cinewise.auth.audit-hash-secret=mysql-admin-it-audit-secret-at-least-32-bytes"
})
@Import(AdminOrderQueryMySqlIntegrationTest.MySqlAdminOrderTestConfiguration.class)
@ContextConfiguration(initializers = AdminOrderQueryMySqlIntegrationTest.DedicatedMySqlSafetyInitializer.class)
class AdminOrderQueryMySqlIntegrationTest {

    private static final String REQUIRED_DATABASE = "cinewise_ticketing_concurrency_check";
    private static final Set<String> ALLOWED_DATABASE_HOSTS = Set.of("localhost", "127.0.0.1", "::1", "mysql");
    private static final long ADMIN_ID = 8_804_000_001L;
    private static final long USER_PAID = 8_804_010_001L;
    private static final long USER_REFUNDED = 8_804_010_002L;
    private static final long PRIMARY_SHOW_ID = 8_804_100_001L;
    private static final long SECONDARY_SHOW_ID = 8_804_100_002L;
    private static final long PAID_ORDER_ID = 8_804_200_001L;
    private static final long REFUNDED_ORDER_ID = 8_804_200_002L;
    private static final long PENDING_ORDER_ID = 8_804_200_003L;
    private static final LocalDateTime BASE_TIME =
            LocalDateTime.of(2026, 8, 4, 9, 0, 0, 123_000_000);

    @Autowired
    private AdminOrderQueryService queryService;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private FakeUserAdminQueryPort userAdminQueryPort;

    private boolean fixtureInitializationStarted;

    @BeforeEach
    void requireDedicatedMySqlAndCreateFixtures() {
        assertThat(jdbcTemplate.queryForObject("SELECT DATABASE()", String.class))
                .as("管理订单MySQL测试只允许操作A的本地隔离库")
                .isEqualTo(REQUIRED_DATABASE);
        // 功能集成覆盖MySQL 8.x查询语义；8.4迁移兼容性由专项迁移门禁验收。
        assertThat(jdbcTemplate.queryForObject("SELECT VERSION()", String.class)).startsWith("8.");
        fixtureInitializationStarted = true;
        cleanupFixtures();
        userAdminQueryPort.reset();
        insertFixtures();
    }

    @AfterEach
    void cleanupMySqlFixtures() {
        if (!fixtureInitializationStarted) {
            return;
        }
        try {
            cleanupFixtures();
        } finally {
            fixtureInitializationStarted = false;
        }
    }

    @Test
    void givenOneHundredMatchedUsers_whenQueryCombinedFilters_thenUseBoundedInCondition() {
        userAdminQueryPort.match("refund@example", oneHundredUserIds());
        userAdminQueryPort.summarize(USER_REFUNDED, "r***@example.com");

        AdminOrderPageView page = queryService.queryOrders(new AdminOrderListQuery(
                null,
                " refund@example ",
                "REFUNDED",
                "8804300002",
                Long.toString(SECONDARY_SHOW_ID),
                LocalDate.of(2026, 8, 4),
                LocalDate.of(2026, 8, 4),
                1,
                20));

        assertThat(page.total()).isEqualTo(1);
        assertThat(page.records()).singleElement().satisfies(order -> {
            assertThat(order.orderNo()).isEqualTo("MYSQL-ADMIN-REFUNDED");
            assertThat(order.userId()).isEqualTo(USER_REFUNDED);
            assertThat(order.emailMasked()).isEqualTo("r***@example.com");
            assertThat(order.orderStatus()).isEqualTo(OrderStatus.REFUNDED);
        });
        assertThat(userAdminQueryPort.lastKeyword()).isEqualTo("refund@example");
        // 展示摘要只查询当前页用户，不把筛选阶段100个ID再传给C。
        assertThat(userAdminQueryPort.lastSummaryIds()).containsExactly(USER_REFUNDED);
    }

    @Test
    void givenEqualCreationTimes_whenPageByOne_thenSortByCreationTimeAndOrderIdDescending() {
        userAdminQueryPort.summarize(USER_PAID, "p***@example.com");
        userAdminQueryPort.summarize(USER_REFUNDED, "r***@example.com");

        AdminOrderPageView firstPage = queryService.queryOrders(emptyQuery(1, 1));
        AdminOrderPageView secondPage = queryService.queryOrders(emptyQuery(2, 1));

        assertThat(firstPage.total()).isEqualTo(3);
        assertThat(firstPage.records()).extracting(AdminOrderView::orderNo)
                .containsExactly("MYSQL-ADMIN-REFUNDED");
        assertThat(secondPage.records()).extracting(AdminOrderView::orderNo)
                .containsExactly("MYSQL-ADMIN-PAID");
    }

    @Test
    void givenRefundedOrder_whenQueryDetail_thenMapMoneyTimeEnumsAndAllSummaries() {
        userAdminQueryPort.summarize(USER_REFUNDED, "r***@example.com");

        AdminOrderView order = queryService.queryOrder("MYSQL-ADMIN-REFUNDED");

        assertThat(order.unitPrice()).isEqualByComparingTo("68.00");
        assertThat(order.totalAmount()).isEqualByComparingTo("136.00");
        assertThat(order.createdAt()).isEqualTo(BASE_TIME);
        assertThat(order.orderStatus()).isEqualTo(OrderStatus.REFUNDED);
        assertThat(order.seats()).extracting(AdminOrderView.SeatView::seatNo)
                .containsExactly("08", "09");
        assertThat(order.payment().amount()).isEqualByComparingTo("136.00");
        assertThat(order.payment().status()).isEqualTo(PaymentStatus.SUCCESS);
        assertThat(order.ticket().status()).isEqualTo(ElectronicTicketStatus.REFUNDED);
        assertThat(order.refund().status()).isEqualTo(RefundStatus.SUCCESS);
        assertThat(order.refund().reason()).isEqualTo("行程变化");
    }

    @Test
    void givenTransactionSnapshots_whenRepeatListAndDetailQueries_thenKeepRowsUnchanged() {
        userAdminQueryPort.summarize(USER_PAID, "p***@example.com");
        userAdminQueryPort.summarize(USER_REFUNDED, "r***@example.com");
        Map<String, List<Map<String, Object>>> before = transactionState();

        queryService.queryOrders(emptyQuery(1, 100));
        queryService.queryOrder("MYSQL-ADMIN-REFUNDED");
        queryService.queryOrders(emptyQuery(1, 100));

        assertThat(transactionState()).isEqualTo(before);
    }

    private AdminOrderListQuery emptyQuery(int page, int size) {
        return new AdminOrderListQuery(null, null, null, null, null, null, null, page, size);
    }

    private Set<Long> oneHundredUserIds() {
        Set<Long> userIds = new LinkedHashSet<>();
        userIds.add(USER_REFUNDED);
        for (long offset = 1; offset < 100; offset++) {
            userIds.add(USER_REFUNDED + offset);
        }
        return Set.copyOf(userIds);
    }

    private void insertFixtures() {
        insertShow(PRIMARY_SHOW_ID, 8_804_300_001L, 8_804_400_001L, BASE_TIME.plusDays(1));
        insertShow(SECONDARY_SHOW_ID, 8_804_300_002L, 8_804_400_002L, BASE_TIME.plusDays(2));
        insertOrder(
                PAID_ORDER_ID,
                "MYSQL-ADMIN-PAID",
                USER_PAID,
                PRIMARY_SHOW_ID,
                1,
                "58.00",
                "58.00",
                "PAID",
                3,
                BASE_TIME);
        insertOrder(
                REFUNDED_ORDER_ID,
                "MYSQL-ADMIN-REFUNDED",
                USER_REFUNDED,
                SECONDARY_SHOW_ID,
                2,
                "68.00",
                "136.00",
                "REFUNDED",
                5,
                BASE_TIME);
        insertOrder(
                PENDING_ORDER_ID,
                "MYSQL-ADMIN-PENDING",
                USER_PAID,
                PRIMARY_SHOW_ID,
                1,
                "58.00",
                "58.00",
                "PENDING_PAYMENT",
                0,
                BASE_TIME.minusDays(1));
        insertPaidAssociations();
        insertRefundedAssociations();
    }

    private void insertShow(long showId, long movieId, long cinemaId, LocalDateTime startTime) {
        jdbcTemplate.update("""
                INSERT INTO movie_show (
                    id, movie_id, cinema_id, auditorium_id, start_time, end_time,
                    language_version, base_price, data_type, source, status, version,
                    create_time, update_time
                ) VALUES (?, ?, ?, ?, ?, ?, '国语2D', 68.00, 'MOCK', 'mysql-admin-it',
                          'ON_SALE', 0, ?, ?)
                """,
                showId,
                movieId,
                cinemaId,
                showId + 1_000L,
                startTime,
                startTime.plusHours(2),
                BASE_TIME,
                BASE_TIME);
    }

    private void insertOrder(
            long orderId,
            String orderNo,
            long userId,
            long showId,
            int ticketCount,
            String unitPrice,
            String totalAmount,
            String status,
            int version,
            LocalDateTime createTime) {
        boolean paid = status.equals("PAID") || status.equals("REFUNDED");
        jdbcTemplate.update("""
                INSERT INTO ticket_order (
                    id, order_no, user_id, show_id, ticket_count, unit_price, total_amount,
                    status, expire_time, client_request_id, idempotency_key, version,
                    paid_time, cancelled_time, refunded_time, create_time, update_time
                ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, NULL, ?, ?, ?)
                """,
                orderId,
                orderNo,
                userId,
                showId,
                ticketCount,
                new BigDecimal(unitPrice),
                new BigDecimal(totalAmount),
                status,
                createTime.plusMinutes(15),
                "mysql-admin-client-" + orderId,
                "mysql-admin-key-" + orderId,
                version,
                paid ? createTime.plusMinutes(5) : null,
                status.equals("REFUNDED") ? createTime.plusMinutes(30) : null,
                createTime,
                createTime.plusMinutes(31));
    }

    private void insertPaidAssociations() {
        jdbcTemplate.update("""
                INSERT INTO mock_payment (
                    id, payment_no, order_id, idempotency_key, amount, status,
                    request_time, paid_time, version, create_time, update_time
                ) VALUES (?, 'MYSQL-PAY-PAID', ?, 'mysql-admin-payment-paid', 58.00,
                          'SUCCESS', ?, ?, 1, ?, ?)
                """,
                8_804_500_001L,
                PAID_ORDER_ID,
                BASE_TIME.plusMinutes(1),
                BASE_TIME.plusMinutes(5),
                BASE_TIME.plusMinutes(1),
                BASE_TIME.plusMinutes(5));
        jdbcTemplate.update("""
                INSERT INTO electronic_ticket (
                    id, ticket_code, order_id, user_id, status, qr_payload, issued_time,
                    invalidated_time, version, create_time, update_time
                ) VALUES (?, 'MYSQL-TICKET-PAID', ?, ?, 'VALID', 'mysql-admin-qr-paid',
                          ?, NULL, 0, ?, ?)
                """,
                8_804_600_001L,
                PAID_ORDER_ID,
                USER_PAID,
                BASE_TIME.plusMinutes(5),
                BASE_TIME.plusMinutes(5),
                BASE_TIME.plusMinutes(5));
    }

    private void insertRefundedAssociations() {
        jdbcTemplate.update("""
                INSERT INTO ticket_order_seat (
                    id, order_id, show_seat_id, row_no_snapshot, seat_no_snapshot,
                    unit_price, create_time, update_time
                ) VALUES
                    (?, ?, ?, 'B', '08', 68.00, ?, ?),
                    (?, ?, ?, 'B', '09', 68.00, ?, ?)
                """,
                8_804_700_001L,
                REFUNDED_ORDER_ID,
                8_804_710_001L,
                BASE_TIME,
                BASE_TIME,
                8_804_700_002L,
                REFUNDED_ORDER_ID,
                8_804_710_002L,
                BASE_TIME,
                BASE_TIME);
        jdbcTemplate.update("""
                INSERT INTO mock_payment (
                    id, payment_no, order_id, idempotency_key, amount, status,
                    request_time, paid_time, version, create_time, update_time
                ) VALUES (?, 'MYSQL-PAY-REFUNDED', ?, 'mysql-admin-payment-refunded', 136.00,
                          'SUCCESS', ?, ?, 2, ?, ?)
                """,
                8_804_500_002L,
                REFUNDED_ORDER_ID,
                BASE_TIME.plusMinutes(1),
                BASE_TIME.plusMinutes(5),
                BASE_TIME.plusMinutes(1),
                BASE_TIME.plusMinutes(5));
        jdbcTemplate.update("""
                INSERT INTO electronic_ticket (
                    id, ticket_code, order_id, user_id, status, qr_payload, issued_time,
                    invalidated_time, version, create_time, update_time
                ) VALUES (?, 'MYSQL-TICKET-REFUNDED', ?, ?, 'REFUNDED',
                          'mysql-admin-qr-refunded', ?, ?, 2, ?, ?)
                """,
                8_804_600_002L,
                REFUNDED_ORDER_ID,
                USER_REFUNDED,
                BASE_TIME.plusMinutes(5),
                BASE_TIME.plusMinutes(30),
                BASE_TIME.plusMinutes(5),
                BASE_TIME.plusMinutes(30));
        jdbcTemplate.update("""
                INSERT INTO refund_request (
                    id, refund_no, order_id, user_id, idempotency_key, action_id, reason,
                    impact_snapshot, status, request_time, processed_time, version,
                    create_time, update_time
                ) VALUES (?, 'MYSQL-REFUND-SUCCESS', ?, ?, 'mysql-admin-refund-key',
                          'mysql-admin-action', '行程变化', JSON_OBJECT('source', 'mysql-admin-it'),
                          'SUCCESS', ?, ?, 3, ?, ?)
                """,
                8_804_800_001L,
                REFUNDED_ORDER_ID,
                USER_REFUNDED,
                BASE_TIME.plusMinutes(20),
                BASE_TIME.plusMinutes(30),
                BASE_TIME.plusMinutes(20),
                BASE_TIME.plusMinutes(30));
    }

    /** 比对查询前后全部A交易夹具，包括状态、版本和更新时间。 */
    private Map<String, List<Map<String, Object>>> transactionState() {
        Map<String, List<Map<String, Object>>> state = new LinkedHashMap<>();
        state.put("orders", jdbcTemplate.queryForList("""
                SELECT id, status, version, paid_time, refunded_time, update_time
                  FROM ticket_order
                 WHERE id IN (?, ?, ?)
                 ORDER BY id
                """, PAID_ORDER_ID, REFUNDED_ORDER_ID, PENDING_ORDER_ID));
        state.put("payments", jdbcTemplate.queryForList("""
                SELECT id, status, version, paid_time, update_time
                  FROM mock_payment
                 WHERE order_id IN (?, ?)
                 ORDER BY id
                """, PAID_ORDER_ID, REFUNDED_ORDER_ID));
        state.put("tickets", jdbcTemplate.queryForList("""
                SELECT id, status, version, invalidated_time, update_time
                  FROM electronic_ticket
                 WHERE order_id IN (?, ?)
                 ORDER BY id
                """, PAID_ORDER_ID, REFUNDED_ORDER_ID));
        state.put("refunds", jdbcTemplate.queryForList("""
                SELECT id, status, version, processed_time, update_time
                  FROM refund_request
                 WHERE order_id = ?
                """, REFUNDED_ORDER_ID));
        state.put("seats", jdbcTemplate.queryForList("""
                SELECT id, order_id, show_seat_id, row_no_snapshot, seat_no_snapshot,
                       unit_price, update_time
                  FROM ticket_order_seat
                 WHERE order_id = ?
                 ORDER BY id
                """, REFUNDED_ORDER_ID));
        return state;
    }

    /** 只删除本测试预留ID，不使用全表DELETE或重置种子座位。 */
    private void cleanupFixtures() {
        jdbcTemplate.update(
                "DELETE FROM refund_request WHERE order_id IN (?, ?, ?)",
                PAID_ORDER_ID,
                REFUNDED_ORDER_ID,
                PENDING_ORDER_ID);
        jdbcTemplate.update(
                "DELETE FROM electronic_ticket WHERE order_id IN (?, ?, ?)",
                PAID_ORDER_ID,
                REFUNDED_ORDER_ID,
                PENDING_ORDER_ID);
        jdbcTemplate.update(
                "DELETE FROM mock_payment WHERE order_id IN (?, ?, ?)",
                PAID_ORDER_ID,
                REFUNDED_ORDER_ID,
                PENDING_ORDER_ID);
        jdbcTemplate.update(
                "DELETE FROM ticket_order_seat WHERE order_id IN (?, ?, ?)",
                PAID_ORDER_ID,
                REFUNDED_ORDER_ID,
                PENDING_ORDER_ID);
        jdbcTemplate.update(
                "DELETE FROM ticket_order_operation WHERE order_id IN (?, ?, ?)",
                PAID_ORDER_ID,
                REFUNDED_ORDER_ID,
                PENDING_ORDER_ID);
        jdbcTemplate.update(
                "DELETE FROM ticket_order WHERE id IN (?, ?, ?)",
                PAID_ORDER_ID,
                REFUNDED_ORDER_ID,
                PENDING_ORDER_ID);
        jdbcTemplate.update(
                "DELETE FROM movie_show WHERE id IN (?, ?)",
                PRIMARY_SHOW_ID,
                SECONDARY_SHOW_ID);
    }

    @TestConfiguration(proxyBeanMethods = false)
    static class MySqlAdminOrderTestConfiguration {

        @Bean
        @Primary
        CurrentUserAccessor adminCurrentUserAccessor() {
            return () -> new CurrentUser(ADMIN_ID, RoleCode.ADMIN, 0L);
        }

        @Bean
        @Primary
        FakeUserAdminQueryPort fakeUserAdminQueryPort() {
            return new FakeUserAdminQueryPort();
        }
    }

    /** 在Spring刷新上下文和创建DataSource之前拒绝非隔离MySQL目标。 */
    static final class DedicatedMySqlSafetyInitializer
            implements ApplicationContextInitializer<ConfigurableApplicationContext> {

        @Override
        public void initialize(ConfigurableApplicationContext applicationContext) {
            validateDataSourceUrl(applicationContext.getEnvironment()
                    .getRequiredProperty("spring.datasource.url"));
        }

        /** 只允许本机或GitHub Actions的MySQL服务名连接固定隔离库。 */
        static void validateDataSourceUrl(String dataSourceUrl) {
            URI uri;
            try {
                if (dataSourceUrl == null || !dataSourceUrl.startsWith("jdbc:mysql://")) {
                    throw new IllegalArgumentException("JDBC URL must use MySQL");
                }
                uri = URI.create(dataSourceUrl.substring("jdbc:".length()));
            } catch (IllegalArgumentException exception) {
                throw new IllegalStateException("管理订单MySQL测试的数据源URL不合法", exception);
            }

            String host = uri.getHost();
            String path = uri.getPath();
            String database = path != null && path.startsWith("/") ? path.substring(1) : null;
            if (!ALLOWED_DATABASE_HOSTS.contains(host) || !REQUIRED_DATABASE.equals(database)) {
                throw new IllegalStateException(
                        "管理订单MySQL测试只允许本机或CI MySQL服务上的固定隔离库");
            }
        }
    }

    /** 只替代C尚未合入的公开端口，不查询sys_user或实现邮箱匹配规则。 */
    static final class FakeUserAdminQueryPort implements UserAdminQueryPort {

        private final Map<String, Set<Long>> matches = new LinkedHashMap<>();
        private final Map<Long, UserAdminSummary> summaries = new LinkedHashMap<>();
        private String lastKeyword;
        private Set<Long> lastSummaryIds = Set.of();

        void reset() {
            matches.clear();
            summaries.clear();
            lastKeyword = null;
            lastSummaryIds = Set.of();
        }

        void match(String keyword, Set<Long> userIds) {
            matches.put(keyword, Set.copyOf(userIds));
        }

        void summarize(long userId, String emailMasked) {
            summaries.put(userId, new UserAdminSummary(userId, emailMasked));
        }

        String lastKeyword() {
            return lastKeyword;
        }

        Set<Long> lastSummaryIds() {
            return lastSummaryIds;
        }

        @Override
        public Set<Long> findUserIdsByKeyword(String userKeyword) {
            lastKeyword = userKeyword;
            return matches.getOrDefault(userKeyword, Set.of());
        }

        @Override
        public Map<Long, UserAdminSummary> findByUserIds(Set<Long> userIds) {
            lastSummaryIds = Set.copyOf(userIds);
            Map<Long, UserAdminSummary> result = new LinkedHashMap<>();
            userIds.forEach(userId -> {
                UserAdminSummary summary = summaries.get(userId);
                if (summary != null) {
                    result.put(userId, summary);
                }
            });
            return Map.copyOf(result);
        }
    }
}
