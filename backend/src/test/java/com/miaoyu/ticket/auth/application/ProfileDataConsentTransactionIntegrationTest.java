package com.miaoyu.ticket.auth.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.reset;

import java.time.LocalDateTime;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

/** 使用真实 Spring 事务和 MyBatis 同意仓储验证 outbox 失败会回滚 CAS。 */
@SpringBootTest(properties = "cinewise.scheduling.enabled=false")
@ActiveProfiles("test")
class ProfileDataConsentTransactionIntegrationTest {
    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private ProfileDataConsentService consentService;

    @MockitoBean
    private ProfileDataConsentOutboxRepository outboxRepository;

    @BeforeEach
    void setUpTable() {
        reset(outboxRepository);
        jdbcTemplate.execute("DROP TABLE IF EXISTS sys_profile_data_consent");
        jdbcTemplate.execute("""
                CREATE TABLE sys_profile_data_consent (
                    id BIGINT PRIMARY KEY,
                    user_id BIGINT NOT NULL UNIQUE,
                    status VARCHAR(16) NOT NULL,
                    consent_version BIGINT NOT NULL,
                    privacy_policy_version VARCHAR(32) NOT NULL,
                    granted_at TIMESTAMP(3),
                    withdrawn_at TIMESTAMP(3),
                    version BIGINT NOT NULL,
                    create_time TIMESTAMP(3) NOT NULL,
                    update_time TIMESTAMP(3) NOT NULL
                )
                """);
        LocalDateTime now = LocalDateTime.of(2026, 8, 7, 0, 0);
        jdbcTemplate.update("""
                INSERT INTO sys_profile_data_consent (
                    id, user_id, status, consent_version, privacy_policy_version,
                    granted_at, withdrawn_at, version, create_time, update_time
                ) VALUES (?, ?, 'GRANTED', 2, '2026-08', ?, NULL, 4, ?, ?)
                """, 9001L, 1001L, now.minusDays(1), now.minusDays(1), now.minusDays(1));
    }

    @Test
    void shouldRollbackConsentWithdrawalWhenOutboxInsertFails() {
        doThrow(new IllegalStateException("outbox unavailable"))
                .when(outboxRepository).insert(any());

        assertThatThrownBy(() -> consentService.withdraw(1001L))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("outbox unavailable");

        String status = jdbcTemplate.queryForObject(
                "SELECT status FROM sys_profile_data_consent WHERE user_id = 1001", String.class);
        Long version = jdbcTemplate.queryForObject(
                "SELECT version FROM sys_profile_data_consent WHERE user_id = 1001", Long.class);
        assertThat(status).isEqualTo("GRANTED");
        assertThat(version).isEqualTo(4L);
    }
}
