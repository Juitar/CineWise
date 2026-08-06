package com.miaoyu.ticket.auth.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.miaoyu.ticket.auth.application.PasswordResetTransaction;
import com.miaoyu.ticket.auth.application.PasswordVerifier;
import com.miaoyu.ticket.auth.application.AccessTokenService;
import com.miaoyu.ticket.auth.application.AuthUserRepository;
import com.miaoyu.ticket.auth.application.VerificationCodeRateLimiter;
import com.miaoyu.ticket.auth.application.VerificationEmailSender;
import com.miaoyu.ticket.auth.domain.VerificationPurpose;
import jakarta.servlet.http.Cookie;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.AfterEach;
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
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

@ActiveProfiles("test")
@SpringBootTest
@AutoConfigureMockMvc
@Import(PasswordResetControllerIntegrationTest.ResetTestConfiguration.class)
class PasswordResetControllerIntegrationTest {

    private static final String CSRF_COOKIE = "cinewise_csrf";
    private static final String ACCESS_COOKIE = "cinewise_access_token";
    private static final String CSRF_HEADER = "X-XSRF-TOKEN";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private CapturingResetEmailSender emailSender;

    @Autowired
    private ControllablePasswordVerifier passwordVerifier;

    @Autowired
    private PasswordResetTransaction passwordResetTransaction;

    @Autowired
    private AccessTokenService accessTokenService;

    @Autowired
    private AuthUserRepository authUserRepository;

    @Autowired
    private JwtDecoder jwtDecoder;

    private ExecutorService executor;

    @BeforeEach
    void setUp() {
        createVerificationFixture();
        jdbcTemplate.update("DELETE FROM sys_email_verify_code");
        jdbcTemplate.update("DELETE FROM sys_login_log");
        jdbcTemplate.update("DELETE FROM sys_user");
        insertUser();
        emailSender.reset();
        passwordVerifier.reset();
        executor = Executors.newFixedThreadPool(2);
    }

    @AfterEach
    void tearDown() {
        executor.shutdownNow();
    }

    @Test
    void shouldPublishResetOpenApiContract() throws Exception {
        mockMvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.components.schemas.PasswordResetRequest.required[*]")
                        .value(containsInAnyOrder("clientRequestId", "email", "code", "newPassword")))
                .andExpect(jsonPath("$.paths['/api/v1/auth/password/reset'].post.security[0].csrfToken")
                        .isArray())
                .andExpect(jsonPath("$.paths['/api/v1/auth/password/reset'].post.responses['422'].description")
                        .value(org.hamcrest.Matchers.containsString("201002")));
    }

    @Test
    void shouldResetPasswordConsumeCodeAndInvalidateOldAndRenewedJwt() throws Exception {
        Cookie oldAccessCookie = login("Password1");
        var oldJwt = jwtDecoder.decode(oldAccessCookie.getValue());
        var renewedToken = accessTokenService.renew(
                        authUserRepository.findById(1001L).orElseThrow(),
                        oldJwt.getClaimAsInstant("sessionStartedAt"))
                .orElseThrow();
        Cookie renewedAccessCookie = new Cookie(ACCESS_COOKIE, renewedToken.value());
        CsrfSession csrf = getCsrf();
        requestCode(csrf, "user@cinewise.test", VerificationPurpose.RESET_PASSWORD);

        mockMvc.perform(post("/api/v1/auth/password/reset")
                        .cookie(csrf.cookie())
                        .header(CSRF_HEADER, csrf.token())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new PasswordResetRequest(
                                "reset-request-1",
                                "user@cinewise.test",
                                emailSender.lastCode(),
                                "NewPassword1"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.changed").value(true))
                .andExpect(jsonPath("$.data.tokenVersion").doesNotExist());

        assertThat(jdbcTemplate.queryForObject(
                        "SELECT status FROM sys_email_verify_code", String.class))
                .isEqualTo("USED");
        assertThat(jdbcTemplate.queryForObject(
                        "SELECT token_version FROM sys_user WHERE id = 1001", Long.class))
                .isEqualTo(1L);
        String passwordHash = jdbcTemplate.queryForObject(
                "SELECT password_hash FROM sys_user WHERE id = 1001", String.class);
        assertThat(passwordEncoder.matches("NewPassword1", passwordHash)).isTrue();
        assertThat(passwordEncoder.matches("Password1", passwordHash)).isFalse();

        mockMvc.perform(get("/api/v1/auth/me").cookie(oldAccessCookie))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value(201006));
        mockMvc.perform(get("/api/v1/auth/me").cookie(renewedAccessCookie))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value(201006));
        assertThat(login("NewPassword1")).isNotNull();
    }

    @Test
    void shouldHideUnknownEmailAndRejectPurposeMismatchWithoutChangingPassword() throws Exception {
        CsrfSession csrf = getCsrf();
        requestCode(csrf, "missing@cinewise.test", VerificationPurpose.RESET_PASSWORD);
        assertThat(emailSender.sendCount()).isZero();
        assertThat(codeCount()).isZero();

        requestCode(csrf, "user@cinewise.test", VerificationPurpose.LOGIN);
        reset(csrf, emailSender.lastCode(), "NewPassword1")
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value(201002));
        assertUnchangedAccountAndUnusedCode();
    }

    @Test
    void shouldRejectExpiredUsedAndMaximumAttemptCodes() throws Exception {
        CsrfSession csrf = getCsrf();
        requestCode(csrf, "user@cinewise.test", VerificationPurpose.RESET_PASSWORD);
        jdbcTemplate.update("UPDATE sys_email_verify_code SET expire_time = ?", LocalDateTime.now().minusMinutes(1));
        reset(csrf, emailSender.lastCode(), "NewPassword1")
                .andExpect(status().isUnprocessableEntity());

        jdbcTemplate.update("DELETE FROM sys_email_verify_code");
        requestCode(csrf, "user@cinewise.test", VerificationPurpose.RESET_PASSWORD);
        jdbcTemplate.update("UPDATE sys_email_verify_code SET status = 'USED', used_time = send_time");
        reset(csrf, emailSender.lastCode(), "NewPassword1")
                .andExpect(status().isUnprocessableEntity());

        jdbcTemplate.update("DELETE FROM sys_email_verify_code");
        requestCode(csrf, "user@cinewise.test", VerificationPurpose.RESET_PASSWORD);
        String wrongCode = emailSender.lastCode().equals("999999") ? "000000" : "999999";
        for (int attempt = 0; attempt < 5; attempt++) {
            reset(csrf, wrongCode, "NewPassword1")
                    .andExpect(status().isUnprocessableEntity())
                    .andExpect(jsonPath("$.code").value(201002));
        }
        assertThat(jdbcTemplate.queryForObject(
                        "SELECT attempt_count FROM sys_email_verify_code", Integer.class))
                .isEqualTo(5);
        assertThat(jdbcTemplate.queryForObject(
                        "SELECT status FROM sys_email_verify_code", String.class))
                .isEqualTo("INVALID");
    }

    @Test
    void shouldRejectWeakPasswordWithoutConsumingCode() throws Exception {
        CsrfSession csrf = getCsrf();
        requestCode(csrf, "user@cinewise.test", VerificationPurpose.RESET_PASSWORD);

        reset(csrf, emailSender.lastCode(), "password")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(101001));
        assertUnchangedAccountAndUnusedCode();
    }

    @Test
    void shouldAllowOnlyOneConcurrentResetForSameCode() throws Exception {
        CsrfSession csrf = getCsrf();
        requestCode(csrf, "user@cinewise.test", VerificationPurpose.RESET_PASSWORD);
        String code = emailSender.lastCode();
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        List<Future<Boolean>> results = new ArrayList<>();
        for (int index = 0; index < 2; index++) {
            results.add(executor.submit(() -> {
                ready.countDown();
                start.await();
                try {
                    passwordResetTransaction.execute(
                            "user@cinewise.test", code, "ConcurrentPassword1");
                    return true;
                } catch (RuntimeException exception) {
                    return false;
                }
            }));
        }
        ready.await();
        start.countDown();

        int successCount = 0;
        for (Future<Boolean> result : results) {
            if (result.get()) {
                successCount++;
            }
        }
        assertThat(successCount).isEqualTo(1);
        assertThat(jdbcTemplate.queryForObject(
                        "SELECT token_version FROM sys_user WHERE id = 1001", Long.class))
                .isEqualTo(1L);
        assertThat(jdbcTemplate.queryForObject(
                        "SELECT status FROM sys_email_verify_code", String.class))
                .isEqualTo("USED");
    }

    @Test
    void shouldRollbackCodeAndPasswordWhenEncodingFailsAfterConsumption() throws Exception {
        CsrfSession csrf = getCsrf();
        requestCode(csrf, "user@cinewise.test", VerificationPurpose.RESET_PASSWORD);
        passwordVerifier.failNextEncode();

        reset(csrf, emailSender.lastCode(), "NewPassword1")
                .andExpect(status().is5xxServerError());

        assertUnchangedAccountAndUnusedCode();
    }

    private org.springframework.test.web.servlet.ResultActions reset(
            CsrfSession csrf, String code, String password) throws Exception {
        return mockMvc.perform(post("/api/v1/auth/password/reset")
                .cookie(csrf.cookie())
                .header(CSRF_HEADER, csrf.token())
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(new PasswordResetRequest(
                        "reset-request", "user@cinewise.test", code, password))));
    }

    private void requestCode(CsrfSession csrf, String email, VerificationPurpose purpose) throws Exception {
        mockMvc.perform(post("/api/v1/auth/email-codes")
                        .cookie(csrf.cookie())
                        .header(CSRF_HEADER, csrf.token())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"" + email + "\",\"purpose\":\"" + purpose + "\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.cooldownSeconds").value(60));
    }

    private Cookie login(String password) throws Exception {
        CsrfSession csrf = getCsrf();
        MvcResult result = mockMvc.perform(post("/api/v1/auth/login/password")
                        .cookie(csrf.cookie())
                        .header(CSRF_HEADER, csrf.token())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new PasswordLoginRequest(
                                "login-" + password, "user@cinewise.test", password))))
                .andExpect(status().isOk())
                .andReturn();
        return result.getResponse().getCookie(ACCESS_COOKIE);
    }

    private CsrfSession getCsrf() throws Exception {
        MvcResult result = mockMvc.perform(get("/api/v1/auth/csrf"))
                .andExpect(status().isOk())
                .andReturn();
        JsonNode json = objectMapper.readTree(result.getResponse().getContentAsByteArray());
        return new CsrfSession(json.at("/data/token").asText(), result.getResponse().getCookie(CSRF_COOKIE));
    }

    private void assertUnchangedAccountAndUnusedCode() {
        assertThat(jdbcTemplate.queryForObject(
                        "SELECT token_version FROM sys_user WHERE id = 1001", Long.class))
                .isZero();
        String passwordHash = jdbcTemplate.queryForObject(
                "SELECT password_hash FROM sys_user WHERE id = 1001", String.class);
        assertThat(passwordEncoder.matches("Password1", passwordHash)).isTrue();
        assertThat(jdbcTemplate.queryForObject(
                        "SELECT status FROM sys_email_verify_code", String.class))
                .isEqualTo("UNUSED");
    }

    private int codeCount() {
        return jdbcTemplate.queryForObject("SELECT COUNT(*) FROM sys_email_verify_code", Integer.class);
    }

    private void createVerificationFixture() {
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
    }

    private void insertUser() {
        LocalDateTime now = LocalDateTime.of(2026, 8, 6, 8, 0);
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

    private record CsrfSession(String token, Cookie cookie) {
    }

    @TestConfiguration(proxyBeanMethods = false)
    static class ResetTestConfiguration {

        @Bean
        @Primary
        VerificationCodeRateLimiter resetVerificationCodeRateLimiter() {
            return new AlwaysAllowRateLimiter();
        }

        @Bean
        @Primary
        CapturingResetEmailSender capturingResetEmailSender() {
            return new CapturingResetEmailSender();
        }

        @Bean
        @Primary
        ControllablePasswordVerifier controllablePasswordVerifier(PasswordEncoder encoder) {
            return new ControllablePasswordVerifier(encoder);
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
            // 测试发送器始终明确成功。
        }
    }

    static class CapturingResetEmailSender implements VerificationEmailSender {

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

    static class ControllablePasswordVerifier implements PasswordVerifier {

        private final PasswordEncoder encoder;
        private boolean failNextEncode;

        ControllablePasswordVerifier(PasswordEncoder encoder) {
            this.encoder = encoder;
        }

        @Override
        public boolean matches(String rawPassword, String passwordHash) {
            return rawPassword != null && passwordHash != null && encoder.matches(rawPassword, passwordHash);
        }

        @Override
        public String encode(String rawPassword) {
            if (failNextEncode) {
                failNextEncode = false;
                throw new IllegalStateException("injected password encoding failure");
            }
            return encoder.encode(rawPassword);
        }

        void failNextEncode() {
            failNextEncode = true;
        }

        void reset() {
            failNextEncode = false;
        }
    }
}
