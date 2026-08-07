package com.miaoyu.ticket.auth.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.miaoyu.ticket.profile.application.ProfileDataConsentWithdrawnEvent;
import java.net.URI;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
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
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.ContextConfiguration;

/** 在 CI 一次性 MySQL 8.4 库验证画像同意撤回事务、outbox 重试和人工恢复。 */
@EnabledIfEnvironmentVariable(named = "CINEWISE_MYSQL_PROFILE_CONSENT_IT", matches = "true")
@ActiveProfiles("dev")
@SpringBootTest(properties = {
    "cinewise.seed.enabled=false",
    "spring.flyway.enabled=true",
    "cinewise.scheduling.enabled=false",
    "cinewise.transaction.expiry-job-enabled=false",
    "management.health.redis.enabled=false",
    "cinewise.auth.jwt-secret=profile-consent-it-jwt-secret-at-least-32-bytes",
    "cinewise.auth.audit-hash-secret=profile-consent-it-audit-secret-at-least-32-bytes"
})
@Import(ProfileDataConsentMySqlIntegrationTest.MySqlTestConfiguration.class)
@ContextConfiguration(
    initializers = ProfileDataConsentMySqlIntegrationTest.CiMySqlSafetyInitializer.class)
class ProfileDataConsentMySqlIntegrationTest {

    private static final String REQUIRED_DATABASE = "cinewise_profile_consent_it";
    private static final String REQUIRED_USERNAME = "cinewise_ci";
    private static final Set<String> ALLOWED_HOSTS = Set.of("localhost", "127.0.0.1", "::1", "mysql");
    private static final long SUCCESS_USER_ID = 9_717_000_001L;
    private static final long ROLLBACK_USER_ID = 9_717_000_002L;
    private static final long CAS_USER_ID = 9_717_000_003L;
    private static final long PREEXISTING_OUTBOX_ID = 9_717_100_001L;
    private static final LocalDateTime GRANTED_AT = LocalDateTime.of(2026, 8, 7, 0, 0);

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private ProfileDataConsentService consentService;

    @Autowired
    private ProfileDataConsentRepository consentRepository;

    @Autowired
    private ProfileDataConsentOutboxDeliveryService deliveryService;

    @Autowired
    private ControllablePublisher publisher;

    @BeforeEach
    void requireCiDatabaseAndPrepareFixtures() {
        assertThat(jdbcTemplate.queryForObject("SELECT DATABASE()", String.class))
                .as("画像同意 MySQL 集成测试只能使用 CI 一次性临时库")
                .isEqualTo(REQUIRED_DATABASE);
        assertThat(jdbcTemplate.queryForObject("SELECT VERSION()", String.class)).startsWith("8.4.");
        assertThat(jdbcTemplate.queryForObject("""
                SELECT COUNT(*)
                  FROM flyway_schema_history
                 WHERE version = '017' AND success = 1
                """, Integer.class)).isEqualTo(1);
        cleanupFixtures();
        publisher.reset();
    }

    @AfterEach
    void cleanupMySqlFixtures() {
        cleanupFixtures();
    }

    @Test
    void shouldPersistWithdrawalRetryAndManualRecoveryWithOriginalEventId() {
        insertGranted(SUCCESS_USER_ID);

        ProfileDataConsentService.WithdrawalResult result = consentService.withdraw(SUCCESS_USER_ID);

        assertThat(result.changed()).isTrue();
        assertThat(result.eventId()).isNotBlank();
        assertThat(consentState(SUCCESS_USER_ID)).containsExactly("WITHDRAWN", 2L, 5L);
        assertThat(outboxState(result.eventId()))
                .containsExactly("PENDING", 1, SUCCESS_USER_ID, 2L, 5L, result.eventId());
        assertThat(jdbcTemplate.queryForObject("""
                SELECT TIMESTAMPDIFF(SECOND, update_time, next_attempt_at)
                  FROM sys_profile_data_consent_outbox
                 WHERE event_id = ?
                """, Long.class, result.eventId())).isEqualTo(60L);

        for (int retryCount = 1; retryCount < 10; retryCount++) {
            assertThat(deliveryService.deliverPending(result.eventId())).isFalse();
        }
        assertThat(outboxState(result.eventId()))
                .containsExactly("EXHAUSTED", 10, SUCCESS_USER_ID, 2L, 5L, result.eventId());

        publisher.allowDelivery();
        assertThat(deliveryService.recoverExhausted(result.eventId())).isTrue();
        assertThat(outboxState(result.eventId()))
                .containsExactly("DELIVERED", 10, SUCCESS_USER_ID, 2L, 5L, result.eventId());
        assertThat(publisher.publishedEvents()).singleElement().satisfies(event -> {
            assertThat(event.eventId()).isEqualTo(result.eventId());
            assertThat(event.userId()).isEqualTo(SUCCESS_USER_ID);
            assertThat(event.consentVersion()).isEqualTo(2L);
            assertThat(event.consentRecordVersion()).isEqualTo(5L);
            assertThat(event.occurredAt()).isNotNull();
            assertThat(event.traceId()).isNotBlank();
        });
    }

    @Test
    void shouldRollbackConsentCasWhenRealOutboxInsertViolatesUniqueKey() {
        insertGranted(ROLLBACK_USER_ID);
        insertPreexistingOutbox(ROLLBACK_USER_ID);

        assertThatThrownBy(() -> consentService.withdraw(ROLLBACK_USER_ID))
                .isInstanceOf(DuplicateKeyException.class);

        assertThat(consentState(ROLLBACK_USER_ID)).containsExactly("GRANTED", 2L, 4L);
        assertThat(jdbcTemplate.queryForObject("""
                SELECT COUNT(*)
                  FROM sys_profile_data_consent_outbox
                 WHERE user_id = ? AND consent_record_version = 5
                """, Integer.class, ROLLBACK_USER_ID)).isEqualTo(1);
    }

    @Test
    void shouldRejectStaleConsentRecordVersionWithoutCreatingOutbox() {
        insertGranted(CAS_USER_ID);

        assertThat(consentRepository.withdraw(
                CAS_USER_ID, 3L, Instant.parse("2026-08-07T01:00:00Z"))).isFalse();

        assertThat(consentState(CAS_USER_ID)).containsExactly("GRANTED", 2L, 4L);
        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM sys_profile_data_consent_outbox WHERE user_id = ?",
                Integer.class,
                CAS_USER_ID)).isZero();
    }

    private void insertGranted(long userId) {
        jdbcTemplate.update("""
                INSERT INTO sys_profile_data_consent (
                    id, user_id, status, consent_version, privacy_policy_version,
                    granted_at, withdrawn_at, version, create_time, update_time
                ) VALUES (?, ?, 'GRANTED', 2, '2026-08', ?, NULL, 4, ?, ?)
                """, userId + 100L, userId, GRANTED_AT, GRANTED_AT, GRANTED_AT);
    }

    private void insertPreexistingOutbox(long userId) {
        jdbcTemplate.update("""
                INSERT INTO sys_profile_data_consent_outbox (
                    id, event_id, user_id, consent_version, consent_record_version,
                    occurred_at, trace_id, status, retry_count, next_attempt_at,
                    delivered_at, create_time, update_time
                ) VALUES (?, 'profile-consent-existing-event', ?, 2, 5, ?,
                          'profile-consent-existing-trace', 'PENDING', 0, ?, NULL, ?, ?)
                """, PREEXISTING_OUTBOX_ID, userId, GRANTED_AT, GRANTED_AT, GRANTED_AT, GRANTED_AT);
    }

    private List<Object> consentState(long userId) {
        return jdbcTemplate.queryForList("""
                SELECT status, consent_version, version
                  FROM sys_profile_data_consent
                 WHERE user_id = ?
                """, userId).getFirst().values().stream().toList();
    }

    private List<Object> outboxState(String eventId) {
        return jdbcTemplate.queryForList("""
                SELECT status, retry_count, user_id, consent_version,
                       consent_record_version, event_id
                  FROM sys_profile_data_consent_outbox
                 WHERE event_id = ?
                """, eventId).getFirst().values().stream().toList();
    }

    private void cleanupFixtures() {
        jdbcTemplate.update("""
                DELETE FROM sys_profile_data_consent_outbox
                 WHERE user_id IN (?, ?, ?)
                """, SUCCESS_USER_ID, ROLLBACK_USER_ID, CAS_USER_ID);
        jdbcTemplate.update("""
                DELETE FROM sys_profile_data_consent
                 WHERE user_id IN (?, ?, ?)
                """, SUCCESS_USER_ID, ROLLBACK_USER_ID, CAS_USER_ID);
    }

    @TestConfiguration(proxyBeanMethods = false)
    static class MySqlTestConfiguration {

        @Bean
        @Primary
        ControllablePublisher controllablePublisher() {
            return new ControllablePublisher();
        }
    }

    /** 在 Spring 创建 DataSource 前拒绝共享库和远程数据库。 */
    static final class CiMySqlSafetyInitializer
            implements ApplicationContextInitializer<ConfigurableApplicationContext> {

        @Override
        public void initialize(ConfigurableApplicationContext applicationContext) {
            String dataSourceUrl = applicationContext.getEnvironment()
                    .getRequiredProperty("spring.datasource.url");
            String username = applicationContext.getEnvironment()
                    .getRequiredProperty("spring.datasource.username");
            validateTarget(dataSourceUrl, username);
        }

        static void validateTarget(String dataSourceUrl, String username) {
            URI uri;
            try {
                if (dataSourceUrl == null || !dataSourceUrl.startsWith("jdbc:mysql://")) {
                    throw new IllegalArgumentException("JDBC URL must use MySQL");
                }
                uri = URI.create(dataSourceUrl.substring("jdbc:".length()));
            } catch (IllegalArgumentException exception) {
                throw new IllegalStateException("画像同意 MySQL 测试的数据源 URL 不合法", exception);
            }
            String path = uri.getPath();
            String database = path != null && path.startsWith("/") ? path.substring(1) : null;
            if (!ALLOWED_HOSTS.contains(uri.getHost())
                    || !REQUIRED_DATABASE.equals(database)
                    || !REQUIRED_USERNAME.equals(username)) {
                throw new IllegalStateException("画像同意 MySQL 测试只允许 CI 一次性临时库和账号");
            }
        }
    }

    static final class ControllablePublisher implements ProfileDataConsentWithdrawalPublisher {
        private final List<ProfileDataConsentWithdrawnEvent> publishedEvents = new ArrayList<>();
        private boolean fail = true;

        @Override
        public void publish(ProfileDataConsentWithdrawnEvent event) {
            if (fail) {
                throw new IllegalStateException("模拟 D 暂时不可用");
            }
            publishedEvents.add(event);
        }

        void allowDelivery() {
            fail = false;
        }

        List<ProfileDataConsentWithdrawnEvent> publishedEvents() {
            return List.copyOf(publishedEvents);
        }

        void reset() {
            fail = true;
            publishedEvents.clear();
        }
    }
}
