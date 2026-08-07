package com.miaoyu.ticket.profile.infrastructure.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import com.miaoyu.ticket.auth.application.CurrentUser;
import com.miaoyu.ticket.auth.application.CurrentUserAccessor;
import com.miaoyu.ticket.auth.application.RoleCode;
import com.miaoyu.ticket.order.event.PaymentSucceededEvent;
import com.miaoyu.ticket.profile.application.ProfileBehaviorRecorder;
import com.miaoyu.ticket.profile.application.ProfileDataConsentQuery;
import com.miaoyu.ticket.profile.application.ProfileDataConsentSnapshot;
import java.time.OffsetDateTime;
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

/**
 * 使用 D 的专用 MySQL 库验证真实画像事件落库和事件重放。
 * 测试不执行 Flyway；它只接受已由 A 审核并准备好的表结构，避免在共享或专用库中误跑迁移。
 */
@EnabledIfEnvironmentVariable(named = "CINEWISE_MYSQL_PROFILE_IT", matches = "true")
@ActiveProfiles("dev")
@SpringBootTest(properties = {
    "cinewise.seed.enabled=false",
    "spring.flyway.enabled=false",
    "cinewise.transaction.expiry-job-enabled=false",
    "management.health.redis.enabled=false"
})
@Import(ProfileBehaviorMySqlIntegrationTest.ProfileMySqlTestConfiguration.class)
class ProfileBehaviorMySqlIntegrationTest {
    private static final long TEST_USER_ID = 9_807_000_001L;
    private static final String TEST_DATABASE_ENVIRONMENT_NAME = "CINEWISE_PROFILE_IT_DATABASE";
    private static final String PLAN_EVENT_ID = "mysql-profile-plan-feedback-1";
    private static final String PAYMENT_EVENT_ID = "mysql-profile-payment-1";

    @Autowired
    private ProfileBehaviorRecorder recorder;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @BeforeEach
    void requireDedicatedDatabaseAndCleanFixtures() {
        String requiredDatabase = System.getenv(TEST_DATABASE_ENVIRONMENT_NAME);
        assertThat(requiredDatabase)
                .as("运行画像 MySQL 集成测试前必须显式指定 D 的专用测试库")
                .isNotBlank();
        assertThat(jdbcTemplate.queryForObject("SELECT DATABASE()", String.class))
                .as("画像 MySQL 测试只允许使用显式指定的 D 专用测试库")
                .isEqualTo(requiredDatabase);
        assertThat(jdbcTemplate.queryForObject("SELECT VERSION()", String.class)).startsWith("8.");
        assertProfileTablesReady();
        cleanFixtures();
    }

    @AfterEach
    void cleanAfterTest() {
        cleanFixtures();
    }

    @Test
    void shouldPersistPlanFeedbackAndReplayTheSameEventWithoutDuplicateRows() {
        String planId = "550e8400-e29b-41d4-a716-446655440010";

        ProfileBehaviorRecorder.RecordResult first = recorder.recordPlanAccepted(
                PLAN_EVENT_ID, planId,
                java.time.LocalDateTime.of(2026, 8, 7, 9, 0));
        ProfileBehaviorRecorder.RecordResult replay = recorder.recordPlanAccepted(
                PLAN_EVENT_ID, planId,
                java.time.LocalDateTime.of(2026, 8, 7, 9, 0));

        assertThat(first.accepted()).isTrue();
        assertThat(replay).isEqualTo(first);
        assertThat(countBehaviorEventsByEventId(PLAN_EVENT_ID)).isEqualTo(1);
        assertThat(jdbcTemplate.queryForObject(
                "SELECT target_type FROM user_behavior_event WHERE event_id = ?", String.class,
                PLAN_EVENT_ID)).isEqualTo("PLAN");
    }

    @Test
    void shouldPersistPaidOrderAsShowEvidenceAndDeduplicatePaymentEvent() {
        PaymentSucceededEvent event = new PaymentSucceededEvent(
                PAYMENT_EVENT_ID, "9807000001", "9807000002", "9807000003",
                Long.toString(TEST_USER_ID), "测试区域", OffsetDateTime.parse("2026-08-07T09:00:00+08:00"),
                1L, OffsetDateTime.parse("2026-08-07T09:00:00+08:00"));

        recorder.recordPayment(event);
        recorder.recordPayment(event);

        assertThat(countBehaviorEventsByEventId(PAYMENT_EVENT_ID)).isEqualTo(1);
        assertThat(jdbcTemplate.queryForObject(
                "SELECT event_type FROM user_behavior_event WHERE event_id = ?", String.class,
                PAYMENT_EVENT_ID)).isEqualTo("PAID_ORDER");
        assertThat(jdbcTemplate.queryForObject(
                "SELECT target_type FROM user_behavior_event WHERE event_id = ?", String.class,
                PAYMENT_EVENT_ID)).isEqualTo("SHOW");
    }

    private void assertProfileTablesReady() {
        Integer tableCount = jdbcTemplate.queryForObject("""
                SELECT COUNT(*)
                  FROM information_schema.tables
                 WHERE table_schema = DATABASE()
                   AND table_name IN ('user_preference', 'user_profile_tag',
                                      'user_behavior_event', 'profile_write_request')
                """, Integer.class);
        assertThat(tableCount).isEqualTo(4);
    }

    private int countBehaviorEventsByEventId(String eventId) {
        return jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM user_behavior_event WHERE event_id = ?", Integer.class, eventId);
    }

    private void cleanFixtures() {
        jdbcTemplate.update("DELETE FROM user_behavior_event WHERE user_id = ?", TEST_USER_ID);
        jdbcTemplate.update("DELETE FROM user_profile_tag WHERE user_id = ?", TEST_USER_ID);
        jdbcTemplate.update("DELETE FROM profile_write_request WHERE user_id = ?", TEST_USER_ID);
        jdbcTemplate.update("DELETE FROM user_preference WHERE user_id = ?", TEST_USER_ID);
    }

    @TestConfiguration
    static class ProfileMySqlTestConfiguration {
        @Bean
        @Primary
        CurrentUserAccessor profileTestCurrentUserAccessor() {
            return () -> new CurrentUser(TEST_USER_ID, RoleCode.USER, 0L);
        }

        @Bean
        @Primary
        ProfileDataConsentQuery profileTestConsentQuery() {
            return userId -> userId == TEST_USER_ID
                    ? new ProfileDataConsentSnapshot(true, 1L, 0L, null, null)
                    : ProfileDataConsentSnapshot.notGranted();
        }
    }
}
