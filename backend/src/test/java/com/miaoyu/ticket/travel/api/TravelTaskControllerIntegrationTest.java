package com.miaoyu.ticket.travel.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.miaoyu.ticket.auth.application.CurrentUser;
import com.miaoyu.ticket.auth.application.RoleCode;
import com.miaoyu.ticket.order.event.PaymentSucceededEvent;
import com.miaoyu.ticket.travel.TravelTestProfileConsentConfiguration;
import com.miaoyu.ticket.travel.application.TravelAdviceService;
import com.miaoyu.ticket.travel.application.TravelErrorCode;
import com.miaoyu.ticket.travel.application.TravelTaskApplicationService;
import com.miaoyu.ticket.travel.application.TravelTaskSummary;
import java.time.OffsetDateTime;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

/** 验证建议接口只展示本人已生成快照，且不向页面泄漏位置或路线字段。 */
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
@Import(TravelTestProfileConsentConfiguration.class)
@AutoConfigureMockMvc
class TravelTaskControllerIntegrationTest {

    private static final String REQUIRED_DATABASE = "cinewise_ticketing_concurrency_check";

    private static final long OWNER_ID = 66001L;
    private static final long OTHER_USER_ID = 66002L;

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private TravelTaskApplicationService travelTaskApplicationService;

    @Autowired
    private TravelAdviceService travelAdviceService;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @BeforeEach
    void clearTravelData() {
        assertThat(jdbcTemplate.queryForObject("SELECT DATABASE()", String.class))
                .as("出行任务接口 MySQL 测试只允许操作隔离库")
                .isEqualTo(REQUIRED_DATABASE);
        jdbcTemplate.update("DELETE FROM travel_notification_log");
        jdbcTemplate.update("DELETE FROM travel_advice_snapshot");
        jdbcTemplate.update("DELETE FROM travel_task");
    }

    @Test
    void givenGeneratedAdvice_whenOwnerReads_thenReturnContentWithoutRouteOrLocation() throws Exception {
        TravelTaskSummary task = createTaskWithAdvice();

        mockMvc.perform(get("/api/v1/travel/tasks/{taskId}/advice", task.taskId())
                        .with(authentication(authenticationFor(OWNER_ID)))
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.available").value(true))
                .andExpect(jsonPath("$.data.weatherJson").isString())
                .andExpect(jsonPath("$.data.adviceJson").isString())
                .andExpect(jsonPath("$.data.source").value("DEMO_WEATHER_V1"))
                .andExpect(jsonPath("$.data.dataTime").exists())
                .andExpect(jsonPath("$.data.expiresAt").exists())
                .andExpect(content().string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString(
                        "routeJson"))))
                .andExpect(content().string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString(
                        "longitude"))));
    }

    @Test
    void givenAnotherUsersAdvice_whenReading_thenHideTaskAndSnapshot() throws Exception {
        TravelTaskSummary task = createTaskWithAdvice();

        mockMvc.perform(get("/api/v1/travel/tasks/{taskId}/advice", task.taskId())
                        .with(authentication(authenticationFor(OTHER_USER_ID)))
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value(TravelErrorCode.TASK_NOT_FOUND.code()));
    }

    private TravelTaskSummary createTaskWithAdvice() {
        TravelTaskSummary task = travelTaskApplicationService.ensureTask(new PaymentSucceededEvent(
                "travel-api-event", "77001", "55001", "66001", "44001", Long.toString(OWNER_ID), "西湖区",
                OffsetDateTime.parse("2026-08-05T19:00:00+08:00"), 1L,
                OffsetDateTime.parse("2026-08-04T08:00:00+08:00")));
        Long internalTaskId = jdbcTemplate.queryForObject(
                "SELECT id FROM travel_task WHERE task_id = ?", Long.class, task.taskId());
        travelAdviceService.generate(internalTaskId);
        return task;
    }

    private UsernamePasswordAuthenticationToken authenticationFor(long userId) {
        return UsernamePasswordAuthenticationToken.authenticated(
                new CurrentUser(userId, RoleCode.USER, 0L), "N/A",
                Set.of(new SimpleGrantedAuthority("ROLE_USER")));
    }
}
