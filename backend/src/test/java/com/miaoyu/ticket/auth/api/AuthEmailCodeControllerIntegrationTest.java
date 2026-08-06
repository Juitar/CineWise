package com.miaoyu.ticket.auth.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.cookie;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.miaoyu.ticket.auth.application.VerificationCodeRateLimiter;
import com.miaoyu.ticket.auth.application.VerificationEmailSender;
import com.miaoyu.ticket.auth.application.RegistrationInviteHasher;
import com.miaoyu.ticket.auth.application.RegistrationInviteRepository;
import com.miaoyu.ticket.auth.domain.RegistrationInvite;
import com.miaoyu.ticket.auth.domain.RegistrationInviteStatus;
import com.miaoyu.ticket.auth.domain.VerificationPurpose;
import jakarta.servlet.http.Cookie;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

@ActiveProfiles("test")
@SpringBootTest
@AutoConfigureMockMvc
@Import(AuthEmailCodeControllerIntegrationTest.EmailCodeTestConfiguration.class)
class AuthEmailCodeControllerIntegrationTest {

    private static final String CSRF_COOKIE = "cinewise_csrf";
    private static final String ACCESS_COOKIE = "cinewise_access_token";
    private static final String CSRF_HEADER = "X-XSRF-TOKEN";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private CapturingVerificationEmailSender emailSender;

    @Autowired
    private RegistrationInviteHasher inviteHasher;

    @Autowired
    private RegistrationInviteRepository inviteRepository;

    @BeforeEach
    void setUp() {
        createForwardMigrationFixture();
        jdbcTemplate.update("DELETE FROM sys_registration_invite_use");
        jdbcTemplate.update("DELETE FROM sys_registration_invite");
        jdbcTemplate.update("DELETE FROM sys_email_verify_code");
        jdbcTemplate.update("DELETE FROM sys_login_log");
        jdbcTemplate.update("DELETE FROM sys_user");
        emailSender.reset();
        insertUser();
        insertInvite();
    }

    @Test
    void shouldPublishEmailCodeOpenApiContract() throws Exception {
        mockMvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.components.schemas.SendEmailCodeRequest.required[*]")
                        .value(containsInAnyOrder("email", "purpose")))
                .andExpect(jsonPath("$.components.schemas.EmailCodeLoginRequest.required[*]")
                        .value(containsInAnyOrder("clientRequestId", "email", "code")))
                .andExpect(jsonPath("$.components.schemas.RegisterRequest.required[*]")
                        .value(containsInAnyOrder(
                                "clientRequestId",
                                "email",
                                "code",
                                "inviteCode",
                                "password",
                                "privacyPolicyVersion",
                                "privacyAccepted")))
                .andExpect(jsonPath("$.paths['/api/v1/auth/email-codes'].post.security[0].csrfToken")
                        .isArray())
                .andExpect(jsonPath("$.paths['/api/v1/auth/login/email'].post.security[0].csrfToken")
                        .isArray())
                .andExpect(jsonPath("$.paths['/api/v1/auth/login/email'].post.responses['422'].description")
                        .value(org.hamcrest.Matchers.containsString("201002")))
                .andExpect(jsonPath("$.paths['/api/v1/auth/register'].post.security[0].csrfToken")
                        .isArray());
    }

    @Test
    void shouldSendConsumeOnceAndCreateEmailCodeSession() throws Exception {
        CsrfSession csrf = getCsrf();
        mockMvc.perform(post("/api/v1/auth/email-codes")
                        .cookie(csrf.cookie())
                        .header(CSRF_HEADER, csrf.token())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\" USER@CINEWISE.TEST \",\"purpose\":\"LOGIN\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.cooldownSeconds").value(60))
                .andExpect(jsonPath("$.data.expiresInSeconds").value(300));

        String code = emailSender.lastCode();
        assertThat(code).matches("\\d{6}");
        assertThat(jdbcTemplate.queryForObject(
                        "SELECT code_hash FROM sys_email_verify_code", String.class))
                .hasSize(64)
                .isNotEqualTo(code);

        MvcResult login = mockMvc.perform(post("/api/v1/auth/login/email")
                        .cookie(csrf.cookie())
                        .header(CSRF_HEADER, csrf.token())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new EmailCodeLoginRequest("request-email-1", "user@cinewise.test", code))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id").value("1001"))
                .andExpect(cookie().httpOnly(ACCESS_COOKIE, true))
                .andReturn();
        assertThat(login.getResponse().getCookie(ACCESS_COOKIE)).isNotNull();
        assertThat(jdbcTemplate.queryForObject(
                        "SELECT status FROM sys_email_verify_code", String.class))
                .isEqualTo("USED");
        assertThat(jdbcTemplate.queryForObject(
                        "SELECT login_type FROM sys_login_log WHERE success = 1", String.class))
                .isEqualTo("EMAIL_CODE");

        CsrfSession retryCsrf = getCsrf();
        mockMvc.perform(post("/api/v1/auth/login/email")
                        .cookie(retryCsrf.cookie())
                        .header(CSRF_HEADER, retryCsrf.token())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new EmailCodeLoginRequest("request-email-2", "user@cinewise.test", code))))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value(201002));
    }

    @Test
    void shouldHideUnknownEmailWithoutSendingOrPersistingCode() throws Exception {
        CsrfSession csrf = getCsrf();
        mockMvc.perform(post("/api/v1/auth/email-codes")
                        .cookie(csrf.cookie())
                        .header(CSRF_HEADER, csrf.token())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"missing@cinewise.test\",\"purpose\":\"LOGIN\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.cooldownSeconds").value(60));

        assertThat(emailSender.sendCount()).isZero();
        assertThat(jdbcTemplate.queryForObject(
                        "SELECT COUNT(*) FROM sys_email_verify_code", Integer.class))
                .isZero();
    }

    @Test
    void shouldCommitFailedVerificationAttempt() throws Exception {
        CsrfSession csrf = getCsrf();
        mockMvc.perform(post("/api/v1/auth/email-codes")
                        .cookie(csrf.cookie())
                        .header(CSRF_HEADER, csrf.token())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"user@cinewise.test\",\"purpose\":\"LOGIN\"}"))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/v1/auth/login/email")
                        .cookie(csrf.cookie())
                        .header(CSRF_HEADER, csrf.token())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new EmailCodeLoginRequest("failed-attempt-1", "user@cinewise.test", "000000"))))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value(201002));

        assertThat(jdbcTemplate.queryForObject(
                        "SELECT attempt_count FROM sys_email_verify_code", Integer.class))
                .isEqualTo(1);
    }

    @Test
    void shouldRequireCsrfAndRejectUnsupportedPurpose() throws Exception {
        mockMvc.perform(post("/api/v1/auth/email-codes")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"user@cinewise.test\",\"purpose\":\"LOGIN\"}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value(201009));

        CsrfSession csrf = getCsrf();
        mockMvc.perform(post("/api/v1/auth/email-codes")
                        .cookie(csrf.cookie())
                        .header(CSRF_HEADER, csrf.token())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"user@cinewise.test\",\"purpose\":\"UNSUPPORTED\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(101001));
    }

    @Test
    void shouldRegisterWithEmailCodeAndSafelyReplay() throws Exception {
        CsrfSession csrf = getCsrf();
        requestRegistrationCode(csrf, "new-user@cinewise.test");
        String code = emailSender.lastCode();
        RegisterRequest request = new RegisterRequest(
                "registration-request-1",
                "new-user@cinewise.test",
                code,
                "training-invite",
                "Password1",
                null,
                "2026-08-03",
                true);

        mockMvc.perform(post("/api/v1/auth/register")
                        .cookie(csrf.cookie())
                        .header(CSRF_HEADER, csrf.token())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.nickname").value(org.hamcrest.Matchers.startsWith("用户")))
                .andExpect(cookie().httpOnly(ACCESS_COOKIE, true));

        assertThat(jdbcTemplate.queryForObject(
                        "SELECT status FROM sys_email_verify_code", String.class))
                .isEqualTo("USED");
        assertThat(jdbcTemplate.queryForObject(
                        "SELECT used_count FROM sys_registration_invite", Integer.class))
                .isEqualTo(1);
        assertThat(jdbcTemplate.queryForObject(
                        "SELECT COUNT(*) FROM sys_registration_invite_use", Integer.class))
                .isEqualTo(1);
        assertThat(jdbcTemplate.queryForObject(
                        "SELECT COUNT(*) FROM sys_user", Integer.class))
                .isEqualTo(2);

        CsrfSession replayCsrf = getCsrf();
        mockMvc.perform(post("/api/v1/auth/register")
                        .cookie(replayCsrf.cookie())
                        .header(CSRF_HEADER, replayCsrf.token())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(cookie().httpOnly(ACCESS_COOKIE, true));
        assertThat(jdbcTemplate.queryForObject(
                        "SELECT used_count FROM sys_registration_invite", Integer.class))
                .isEqualTo(1);

        CsrfSession mismatchCsrf = getCsrf();
        RegisterRequest mismatch = new RegisterRequest(
                request.clientRequestId(),
                request.email(),
                request.code(),
                request.inviteCode(),
                "Different1",
                request.nickname(),
                request.privacyPolicyVersion(),
                true);
        mockMvc.perform(post("/api/v1/auth/register")
                        .cookie(mismatchCsrf.cookie())
                        .header(CSRF_HEADER, mismatchCsrf.token())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(mismatch)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(101001));
    }

    @Test
    void shouldEnforceInviteStatusTimeUsageAndVersionConditions() {
        String hash = inviteHasher.hash("training-invite");
        LocalDateTime now = LocalDateTime.now();

        jdbcTemplate.update("UPDATE sys_registration_invite SET status = 'DISABLED' WHERE id = 2001");
        assertThat(inviteRepository.consume(2001L, 0, now)).isFalse();

        jdbcTemplate.update("""
                UPDATE sys_registration_invite
                   SET status = 'ENABLED', valid_from = ?, expire_time = ?, used_count = 0
                 WHERE id = 2001
                """, now.plusMinutes(1), now.plusDays(1));
        assertThat(inviteRepository.consume(2001L, 0, now)).isFalse();

        jdbcTemplate.update("""
                UPDATE sys_registration_invite
                   SET valid_from = ?, expire_time = ?
                 WHERE id = 2001
                """, now.minusDays(2), now.minusDays(1));
        assertThat(inviteRepository.consume(2001L, 0, now)).isFalse();

        jdbcTemplate.update("""
                UPDATE sys_registration_invite
                   SET expire_time = ?, used_count = max_uses
                 WHERE id = 2001
                """, now.plusDays(1));
        assertThat(inviteRepository.consume(2001L, 0, now)).isFalse();

        jdbcTemplate.update("""
                UPDATE sys_registration_invite
                   SET used_count = 0, valid_from = ?, expire_time = ?
                 WHERE id = 2001
                """, now.minusDays(1), now.plusDays(1));
        RegistrationInvite invite = inviteRepository.findByCodeHash(hash).orElseThrow();
        assertThat(inviteRepository.consume(invite.id(), invite.version(), now)).isTrue();
        assertThat(inviteRepository.consume(invite.id(), invite.version(), now)).isFalse();
    }

    @Test
    void shouldInsertInitialInviteOnceWithoutResettingExistingUsage() {
        LocalDateTime now = LocalDateTime.now();
        RegistrationInvite initial = new RegistrationInvite(
                2002L,
                "b".repeat(64),
                RegistrationInviteStatus.ENABLED,
                100,
                0,
                now.minusDays(1),
                now.plusDays(30),
                0);

        assertThat(inviteRepository.createIfAbsent(initial, now)).isTrue();
        jdbcTemplate.update("""
                UPDATE sys_registration_invite
                   SET used_count = 7, version = 7, status = 'DISABLED'
                 WHERE id = 2002
                """);

        assertThat(inviteRepository.createIfAbsent(initial, now.plusMinutes(1))).isFalse();
        assertThat(jdbcTemplate.queryForObject(
                        "SELECT used_count FROM sys_registration_invite WHERE id = 2002", Integer.class))
                .isEqualTo(7);
        assertThat(jdbcTemplate.queryForObject(
                        "SELECT version FROM sys_registration_invite WHERE id = 2002", Long.class))
                .isEqualTo(7L);
        assertThat(jdbcTemplate.queryForObject(
                        "SELECT status FROM sys_registration_invite WHERE id = 2002", String.class))
                .isEqualTo("DISABLED");
    }

    @Test
    void shouldRollbackVerificationCodeWhenInviteIsUnavailable() throws Exception {
        CsrfSession csrf = getCsrf();
        requestRegistrationCode(csrf, "rollback@cinewise.test");

        mockMvc.perform(post("/api/v1/auth/register")
                        .cookie(csrf.cookie())
                        .header(CSRF_HEADER, csrf.token())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new RegisterRequest(
                                "registration-request-2",
                                "rollback@cinewise.test",
                                emailSender.lastCode(),
                                "wrong-invite",
                                "Password1",
                                "回滚测试",
                                "2026-08-03",
                                true))))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value(201004));

        assertThat(jdbcTemplate.queryForObject(
                        "SELECT status FROM sys_email_verify_code", String.class))
                .isEqualTo("UNUSED");
        assertThat(jdbcTemplate.queryForObject(
                        "SELECT used_count FROM sys_registration_invite", Integer.class))
                .isZero();
        assertThat(jdbcTemplate.queryForObject(
                        "SELECT COUNT(*) FROM sys_user", Integer.class))
                .isEqualTo(1);
    }

    @Test
    void shouldRejectPrivacyRefusalBeforeChangingRegistrationData() throws Exception {
        CsrfSession csrf = getCsrf();
        requestRegistrationCode(csrf, "privacy@cinewise.test");

        mockMvc.perform(post("/api/v1/auth/register")
                        .cookie(csrf.cookie())
                        .header(CSRF_HEADER, csrf.token())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new RegisterRequest(
                                "registration-request-3",
                                "privacy@cinewise.test",
                                emailSender.lastCode(),
                                "training-invite",
                                "Password1",
                                null,
                                "2026-08-03",
                                false))))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value(201008));
        assertThat(jdbcTemplate.queryForObject(
                        "SELECT status FROM sys_email_verify_code", String.class))
                .isEqualTo("UNUSED");
    }

    private void requestRegistrationCode(CsrfSession csrf, String email) throws Exception {
        mockMvc.perform(post("/api/v1/auth/email-codes")
                        .cookie(csrf.cookie())
                        .header(CSRF_HEADER, csrf.token())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"" + email + "\",\"purpose\":\"REGISTER\"}"))
                .andExpect(status().isOk());
    }

    private CsrfSession getCsrf() throws Exception {
        MvcResult result = mockMvc.perform(get("/api/v1/auth/csrf"))
                .andExpect(status().isOk())
                .andReturn();
        JsonNode json = objectMapper.readTree(result.getResponse().getContentAsByteArray());
        return new CsrfSession(json.at("/data/token").asText(), result.getResponse().getCookie(CSRF_COOKIE));
    }

    private void createForwardMigrationFixture() {
        jdbcTemplate.execute("""
                CREATE TABLE IF NOT EXISTS sys_email_verify_code (
                    id BIGINT NOT NULL PRIMARY KEY,
                    email VARCHAR(255) NOT NULL,
                    purpose VARCHAR(32) NOT NULL,
                    code_hash VARCHAR(255) NOT NULL,
                    status VARCHAR(16) NOT NULL DEFAULT 'UNUSED',
                    send_time TIMESTAMP NOT NULL,
                    expire_time TIMESTAMP NOT NULL,
                    used_time TIMESTAMP NULL,
                    attempt_count INT NOT NULL DEFAULT 0,
                    create_time TIMESTAMP NOT NULL,
                    update_time TIMESTAMP NOT NULL
                )
                """);
        jdbcTemplate.execute("""
                CREATE TABLE IF NOT EXISTS sys_registration_invite (
                    id BIGINT NOT NULL PRIMARY KEY,
                    code_hash VARCHAR(64) NOT NULL UNIQUE,
                    status VARCHAR(16) NOT NULL,
                    max_uses INT NOT NULL,
                    used_count INT NOT NULL,
                    valid_from TIMESTAMP NOT NULL,
                    expire_time TIMESTAMP NOT NULL,
                    version BIGINT NOT NULL,
                    create_time TIMESTAMP NOT NULL,
                    update_time TIMESTAMP NOT NULL
                )
                """);
        jdbcTemplate.execute("""
                CREATE TABLE IF NOT EXISTS sys_registration_invite_use (
                    id BIGINT NOT NULL PRIMARY KEY,
                    invite_id BIGINT NOT NULL,
                    user_id BIGINT NOT NULL UNIQUE,
                    client_request_id VARCHAR(64) NOT NULL UNIQUE,
                    used_at TIMESTAMP NOT NULL,
                    create_time TIMESTAMP NOT NULL
                )
                """);
        jdbcTemplate.execute("ALTER TABLE sys_login_log DROP CONSTRAINT IF EXISTS chk_sys_login_log_type");
        jdbcTemplate.execute("""
                ALTER TABLE sys_login_log ADD CONSTRAINT chk_sys_login_log_type
                CHECK (login_type IN ('PASSWORD', 'ADMIN_PASSWORD', 'EMAIL_CODE'))
                """);
    }

    private void insertUser() {
        LocalDateTime now = LocalDateTime.of(2026, 8, 5, 8, 0);
        jdbcTemplate.update("""
                INSERT INTO sys_user (
                    id, email, password_hash, nickname, role_code, status, email_verified,
                    token_version, privacy_policy_version, privacy_accepted_at, version, create_time, update_time
                ) VALUES (?, ?, ?, ?, 'USER', 'NORMAL', 1, 0, ?, ?, 0, ?, ?)
                """,
                1001L,
                "user@cinewise.test",
                passwordEncoder.encode("Password1"),
                "测试用户",
                "2026-08-03",
                now,
                now,
                now);
    }

    private void insertInvite() {
        LocalDateTime now = LocalDateTime.of(2026, 8, 5, 8, 0);
        jdbcTemplate.update("""
                INSERT INTO sys_registration_invite (
                    id, code_hash, status, max_uses, used_count, valid_from,
                    expire_time, version, create_time, update_time
                ) VALUES (?, ?, 'ENABLED', 10, 0, ?, ?, 0, ?, ?)
                """,
                2001L,
                inviteHasher.hash("training-invite"),
                now.minusDays(1),
                now.plusDays(30),
                now,
                now);
    }

    private record CsrfSession(String token, Cookie cookie) {
    }

    @TestConfiguration(proxyBeanMethods = false)
    static class EmailCodeTestConfiguration {

        @Bean
        @Primary
        VerificationCodeRateLimiter verificationCodeRateLimiter() {
            return new AlwaysAllowRateLimiter();
        }

        @Bean
        @Primary
        CapturingVerificationEmailSender capturingVerificationEmailSender() {
            return new CapturingVerificationEmailSender();
        }
    }

    static class AlwaysAllowRateLimiter implements VerificationCodeRateLimiter {

        @Override
        public SendPermit acquire(
                String emailHash,
                String ipHash,
                VerificationPurpose purpose,
                Duration cooldown,
                Duration ipWindow,
                int maximumIpRequests) {
            return new SendPermit(true, true, cooldown.toSeconds());
        }

        @Override
        public void releaseEmailCooldown(String emailHash, VerificationPurpose purpose) {
            // 测试 Provider 始终成功，无需释放。
        }
    }

    static class CapturingVerificationEmailSender implements VerificationEmailSender {

        private final AtomicReference<String> lastCode = new AtomicReference<>();
        private final AtomicInteger sendCount = new AtomicInteger();

        @Override
        public DeliveryResult send(
                String normalizedEmail, String code, VerificationPurpose purpose, String traceId) {
            lastCode.set(code);
            sendCount.incrementAndGet();
            return DeliveryResult.SENT;
        }

        String lastCode() {
            return lastCode.get();
        }

        int sendCount() {
            return sendCount.get();
        }

        void reset() {
            lastCode.set(null);
            sendCount.set(0);
        }
    }
}
