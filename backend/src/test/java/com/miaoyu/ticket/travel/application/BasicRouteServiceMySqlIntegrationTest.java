package com.miaoyu.ticket.travel.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.miaoyu.ticket.auth.application.CurrentUser;
import com.miaoyu.ticket.auth.application.CurrentUserAccessor;
import com.miaoyu.ticket.auth.application.RoleCode;
import com.miaoyu.ticket.common.error.BusinessException;
import com.miaoyu.ticket.geo.domain.LocationGranularity;
import com.miaoyu.ticket.geo.domain.ResolvedGeoPoint;
import com.miaoyu.ticket.order.event.OrderInvalidated;
import java.time.Clock;
import java.time.OffsetDateTime;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.ActiveProfiles;

/** 使用隔离 MySQL 验证 V013 前历史任务不能按影院区域猜测路线终点。 */
@EnabledIfEnvironmentVariable(named = "CINEWISE_MYSQL_TRAVEL_IT", matches = "true")
@ActiveProfiles("dev")
@SpringBootTest(properties = {
    "spring.flyway.enabled=true",
    "cinewise.seed.enabled=true",
    "cinewise.seed.fixed-value=20260802",
    "cinewise.transaction.expiry-job-enabled=false",
    "cinewise.transaction.paid-travel-reconciliation.enabled=false",
    "cinewise.transaction.refunded-travel-reconciliation.enabled=false",
    "management.health.redis.enabled=false"
})
class BasicRouteServiceMySqlIntegrationTest {

    private static final String REQUIRED_DATABASE = "cinewise_ticketing_concurrency_check";
    private static final long OWNER_ID = 55001L;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private TravelTaskApplicationService taskService;

    @Autowired
    private TravelTaskRepository taskRepository;

    @Autowired
    private CurrentUserAccessor currentUserAccessor;

    @Autowired
    private Clock clock;

    @BeforeEach
    void verifyIsolatedDatabaseAndClearRows() {
        assertThat(jdbcTemplate.queryForObject("SELECT DATABASE()", String.class))
                .as("路线 MySQL 测试只允许操作隔离库")
                .isEqualTo(REQUIRED_DATABASE);
        clearRows();
    }

    @AfterEach
    void clearRowsAfterTest() {
        clearRows();
    }

    @Test
    void givenHistoricalTaskWithoutCinemaId_whenPlanning_thenReturnUnavailableWithoutProviderCall() {
        TravelTaskSummary task = taskService.ensureTaskCancelled(new OrderInvalidated(
                "route-history-refund", "99001", "66001", "0", Long.toString(OWNER_ID), "西湖区",
                OffsetDateTime.parse("2026-08-05T19:00:00+08:00"), 1L,
                OffsetDateTime.parse("2026-08-04T08:00:00+08:00"), "REFUNDED"));
        AtomicInteger providerCalls = new AtomicInteger();
        BasicRouteService routeService = new BasicRouteService(
                taskRepository,
                currentUserAccessor,
                (origin, area, mode, requestedAt) -> {
                    providerCalls.incrementAndGet();
                    return Optional.empty();
                },
                ignored -> Optional.of(new ResolvedGeoPoint(
                        new java.math.BigDecimal("120.1"), new java.math.BigDecimal("30.2"),
                        LocationGranularity.ADDRESS)),
                clock);

        SecurityContextHolder.getContext().setAuthentication(
                UsernamePasswordAuthenticationToken.authenticated(
                        new CurrentUser(OWNER_ID, RoleCode.USER, 0L), "test", Set.of()));
        try {
            // 历史任务仍可保留和查询，但路线必须先验证影院 ID，不能使用区域名称代替终点。
            assertThatThrownBy(() -> routeService.planMyRoute(task.taskId(),
                    new BasicRouteCommand(new ResolvedGeoPoint(
                            new java.math.BigDecimal("120.1"), new java.math.BigDecimal("30.2"),
                            LocationGranularity.POI),
                            "TRANSIT", true)))
                    .isInstanceOfSatisfying(BusinessException.class,
                            error -> assertThat(error.getErrorCode())
                                    .isEqualTo(TravelErrorCode.ROUTE_SERVICE_UNAVAILABLE));
            assertThat(providerCalls).hasValue(0);
        } finally {
            SecurityContextHolder.clearContext();
        }
    }

    private void clearRows() {
        jdbcTemplate.update("DELETE FROM travel_notification_log");
        jdbcTemplate.update("DELETE FROM travel_advice_snapshot");
        jdbcTemplate.update("DELETE FROM travel_task");
    }
}
