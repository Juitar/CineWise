package com.miaoyu.ticket.auth.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.cookie;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.Cookie;
import java.time.LocalDateTime;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

@ActiveProfiles("test")
@SpringBootTest
@AutoConfigureMockMvc
class AuthControllerIntegrationTest {

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
    private JwtEncoder jwtEncoder;

    @BeforeEach
    void setUpAccounts() {
        jdbcTemplate.update("DELETE FROM sys_login_log");
        jdbcTemplate.update("DELETE FROM sys_user");
        insertUser(1001L, "user@cinewise.test", "USER", "NORMAL", 0L);
        insertUser(1002L, "admin@cinewise.test", "ADMIN", "NORMAL", 0L);
        insertUser(1003L, "disabled@cinewise.test", "USER", "DISABLED", 0L);
    }

    @Test
    void shouldLoginRestoreCurrentUserLogoutAndRejectOldJwt() throws Exception {
        CsrfSession anonymousCsrf = getCsrf();
        MvcResult login = mockMvc.perform(post("/api/v1/auth/login/password")
                        .cookie(anonymousCsrf.cookie())
                        .header(CSRF_HEADER, anonymousCsrf.token())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(loginJson("request-user-1", " USER@CINEWISE.TEST ", "Password1")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.id").value("1001"))
                .andExpect(jsonPath("$.data.role").value("USER"))
                .andExpect(jsonPath("$.data.emailMasked").value("u***@cinewise.test"))
                .andExpect(jsonPath("$.data.tokenVersion").doesNotExist())
                .andExpect(cookie().httpOnly(ACCESS_COOKIE, true))
                .andReturn();
        Cookie accessCookie = requireCookie(login, ACCESS_COOKIE);

        mockMvc.perform(get("/api/v1/auth/me").cookie(accessCookie))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id").value("1001"));

        CsrfSession authenticatedCsrf = getCsrf(accessCookie);
        mockMvc.perform(post("/api/v1/auth/logout")
                        .cookie(accessCookie, authenticatedCsrf.cookie())
                        .header(CSRF_HEADER, authenticatedCsrf.token()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.loggedOut").value(true))
                .andExpect(cookie().maxAge(ACCESS_COOKIE, 0));

        mockMvc.perform(get("/api/v1/auth/me").cookie(accessCookie))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value(201006));
        assertThat(jdbcTemplate.queryForObject(
                "SELECT token_version FROM sys_user WHERE id = 1001", Long.class)).isEqualTo(1L);
    }

    @Test
    void shouldReturnAuthenticationSpecificErrorsAndAuditFailures() throws Exception {
        mockMvc.perform(post("/api/v1/auth/login/password")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(loginJson("request-no-csrf", "user@cinewise.test", "Password1")))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value(201009));

        CsrfSession csrf = getCsrf();
        mockMvc.perform(post("/api/v1/auth/login/password")
                        .cookie(csrf.cookie())
                        .header(CSRF_HEADER, csrf.token())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(loginJson("request-bad-password", "missing@cinewise.test", "WrongPass1")))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value(201001));

        csrf = getCsrf();
        mockMvc.perform(post("/api/v1/auth/login/password")
                        .cookie(csrf.cookie())
                        .header(CSRF_HEADER, csrf.token())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(loginJson("request-disabled", "disabled@cinewise.test", "Password1")))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value(201005));

        csrf = getCsrf();
        mockMvc.perform(post("/api/v1/admin/auth/login")
                        .cookie(csrf.cookie())
                        .header(CSRF_HEADER, csrf.token())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(loginJson("request-role", "user@cinewise.test", "Password1")))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value(201001));

        csrf = getCsrf();
        mockMvc.perform(post("/api/v1/auth/login/password")
                        .cookie(csrf.cookie())
                        .header(CSRF_HEADER, csrf.token())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"bad\",\"password\":\"short\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(101001));

        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM sys_login_log WHERE success = 0", Integer.class)).isEqualTo(3);
    }

    @Test
    void shouldLoginAdminFromAdminEndpoint() throws Exception {
        CsrfSession csrf = getCsrf();
        mockMvc.perform(post("/api/v1/admin/auth/login")
                        .cookie(csrf.cookie())
                        .header(CSRF_HEADER, csrf.token())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(loginJson("request-admin-1", "admin@cinewise.test", "Password1")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.role").value("ADMIN"));
    }

    @Test
    void shouldAllowXsrfHeaderDuringCorsPreflight() throws Exception {
        String origin = "http://localhost:8000";

        MvcResult result = mockMvc.perform(options("/api/v1/auth/login/password")
                        .header(HttpHeaders.ORIGIN, origin)
                        .header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, "POST")
                        .header(HttpHeaders.ACCESS_CONTROL_REQUEST_HEADERS, "x-xsrf-token"))
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN, origin))
                .andExpect(header().string(HttpHeaders.ACCESS_CONTROL_ALLOW_CREDENTIALS, "true"))
                .andReturn();

        assertThat(result.getResponse().getHeader(HttpHeaders.ACCESS_CONTROL_ALLOW_HEADERS))
                .containsIgnoringCase("x-xsrf-token");
    }

    @Test
    void shouldRejectMissingExpiredDisabledAndInsufficientRoleSessions() throws Exception {
        mockMvc.perform(get("/api/v1/auth/me"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value(201006));

        Cookie expiredCookie = new Cookie(ACCESS_COOKIE, expiredToken(1001L));
        mockMvc.perform(get("/api/v1/auth/me").cookie(expiredCookie))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value(201006));

        Cookie userCookie = loginAndGetAccessCookie("user@cinewise.test", "/api/v1/auth/login/password");
        mockMvc.perform(get("/api/v1/admin/not-implemented").cookie(userCookie))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value(201007));

        jdbcTemplate.update("UPDATE sys_user SET status = 'DISABLED' WHERE id = 1001");
        mockMvc.perform(get("/api/v1/auth/me").cookie(userCookie))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value(201006));
    }

    @Test
    void shouldAllowRepeatedLogoutAndEnforceLoginLogConstraintAndCleanupIndex() throws Exception {
        Cookie accessCookie = loginAndGetAccessCookie("user@cinewise.test", "/api/v1/auth/login/password");
        CsrfSession firstCsrf = getCsrf(accessCookie);
        mockMvc.perform(post("/api/v1/auth/logout")
                        .cookie(accessCookie, firstCsrf.cookie())
                        .header(CSRF_HEADER, firstCsrf.token()))
                .andExpect(status().isOk());

        CsrfSession secondCsrf = getCsrf(accessCookie);
        mockMvc.perform(post("/api/v1/auth/logout")
                        .cookie(accessCookie, secondCsrf.cookie())
                        .header(CSRF_HEADER, secondCsrf.token()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.loggedOut").value(true));

        assertThatThrownBy(() -> jdbcTemplate.update("""
                INSERT INTO sys_login_log (
                    id, user_id, login_type, success, failure_code, trace_id, create_time
                ) VALUES (9999, NULL, 'PASSWORD', 1, NULL, 'invalid-log', CURRENT_TIMESTAMP)
                """)).isInstanceOf(RuntimeException.class);

        Integer cleanupIndexCount = jdbcTemplate.queryForObject("""
                SELECT COUNT(*)
                  FROM information_schema.indexes
                 WHERE table_name = 'sys_login_log'
                   AND index_name = 'idx_login_cleanup_create_time'
                """, Integer.class);
        assertThat(cleanupIndexCount).isEqualTo(1);

        String ipHash = jdbcTemplate.queryForObject(
                "SELECT ip_hash FROM sys_login_log WHERE success = 1 ORDER BY create_time LIMIT 1", String.class);
        assertThat(ipHash).hasSize(64).doesNotContain("127.0.0.1");
        String userAgentSummary = jdbcTemplate.queryForObject(
                "SELECT user_agent_summary FROM sys_login_log WHERE success = 1 ORDER BY create_time LIMIT 1",
                String.class);
        assertThat(userAgentSummary).hasSize(64).doesNotContain("JUnit");
    }

    private CsrfSession getCsrf(Cookie... cookies) throws Exception {
        MockHttpServletRequestBuilder request = get("/api/v1/auth/csrf");
        if (cookies.length > 0) {
            request.cookie(cookies);
        }
        MvcResult result = mockMvc.perform(request)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.headerName").value(CSRF_HEADER))
                .andReturn();
        JsonNode json = objectMapper.readTree(result.getResponse().getContentAsByteArray());
        return new CsrfSession(json.at("/data/token").asText(), requireCookie(result, CSRF_COOKIE));
    }

    private Cookie requireCookie(MvcResult result, String name) {
        Cookie cookie = result.getResponse().getCookie(name);
        assertThat(cookie).as("response cookie %s", name).isNotNull();
        return cookie;
    }

    private Cookie loginAndGetAccessCookie(String email, String endpoint) throws Exception {
        CsrfSession csrf = getCsrf();
        MvcResult login = mockMvc.perform(post(endpoint)
                        .cookie(csrf.cookie())
                        .header(CSRF_HEADER, csrf.token())
                        .header("User-Agent", "JUnit Auth Client")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(loginJson("request-" + UUID.randomUUID(), email, "Password1")))
                .andExpect(status().isOk())
                .andReturn();
        return requireCookie(login, ACCESS_COOKIE);
    }

    private String expiredToken(long userId) {
        Instant now = Instant.now();
        JwtClaimsSet claims = JwtClaimsSet.builder()
                .subject(Long.toString(userId))
                .claim("role", "USER")
                .claim("tokenVersion", 0L)
                .issuedAt(now.minusSeconds(3600))
                .expiresAt(now.minusSeconds(1800))
                .id(UUID.randomUUID().toString())
                .build();
        return jwtEncoder.encode(JwtEncoderParameters.from(
                        JwsHeader.with(MacAlgorithm.HS256).build(), claims))
                .getTokenValue();
    }

    private String loginJson(String requestId, String email, String password) throws Exception {
        return objectMapper.writeValueAsString(new PasswordLoginRequest(requestId, email, password));
    }

    private void insertUser(long id, String email, String role, String status, long tokenVersion) {
        LocalDateTime now = LocalDateTime.of(2026, 8, 3, 8, 0);
        jdbcTemplate.update("""
                INSERT INTO sys_user (
                    id, email, password_hash, nickname, role_code, status, email_verified,
                    token_version, privacy_policy_version, privacy_accepted_at, version, create_time, update_time
                ) VALUES (?, ?, ?, ?, ?, ?, 1, ?, ?, ?, 0, ?, ?)
                """,
                id,
                email,
                passwordEncoder.encode("Password1"),
                role + "测试账号",
                role,
                status,
                tokenVersion,
                "2026-08-03",
                now,
                now,
                now);
    }

    private record CsrfSession(String token, Cookie cookie) {
    }
}
