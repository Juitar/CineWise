package com.miaoyu.ticket.auth.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.Cookie;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

/** 从 CSRF、登录到 Cookie/JWT 过滤器验证管理订单列表和详情，不注入模拟 Authentication。 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class AdminOrderRealAuthenticationIntegrationTest {

    private static final String ACCESS_COOKIE = "cinewise_access_token";
    private static final String CSRF_COOKIE = "cinewise_csrf";
    private static final String CSRF_HEADER = "X-XSRF-TOKEN";
    private static final String LIST_PATH = "/api/v1/admin/orders";
    private static final String DETAIL_PATH = "/api/v1/admin/orders/CW-AUTH-ACCEPTANCE-1";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        SecurityContextHolder.clearContext();
        jdbcTemplate.update("DELETE FROM sys_login_log");
        jdbcTemplate.update("DELETE FROM ticket_order_seat");
        jdbcTemplate.update("DELETE FROM ticket_order_operation");
        jdbcTemplate.update("DELETE FROM ticket_order");
        jdbcTemplate.update("DELETE FROM show_seat");
        jdbcTemplate.update("DELETE FROM movie_show");
        jdbcTemplate.update("DELETE FROM sys_user");
        insertUser(9101L, "user-auth-acceptance@cinewise.test", "USER");
        insertUser(9102L, "admin-auth-acceptance@cinewise.test", "ADMIN");
        insertOrder();
    }

    @Test
    void shouldEnforceRealLoginCookieForAdminOrderListAndDetail() throws Exception {
        Cookie adminCookie = login("admin-auth-acceptance@cinewise.test", "/api/v1/admin/auth/login");
        assertAllowed(LIST_PATH, adminCookie, "$.data.records[0].emailMasked");
        assertAllowed(DETAIL_PATH, adminCookie, "$.data.summary.emailMasked");

        Cookie userCookie = login("user-auth-acceptance@cinewise.test", "/api/v1/auth/login/password");
        assertRejected(LIST_PATH, userCookie, 403, 201007);
        assertRejected(DETAIL_PATH, userCookie, 403, 201007);

        assertRejected(LIST_PATH, null, 401, 201006);
        assertRejected(DETAIL_PATH, null, 401, 201006);
    }

    private void assertAllowed(String path, Cookie cookie, String maskedEmailPath) throws Exception {
        mockMvc.perform(get(path).cookie(cookie).accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath(maskedEmailPath).value("u***@cinewise.test"));
    }

    private void assertRejected(String path, Cookie cookie, int statusCode, int errorCode) throws Exception {
        var request = get(path).accept(MediaType.APPLICATION_JSON);
        if (cookie != null) {
            request.cookie(cookie);
        }
        mockMvc.perform(request)
                .andExpect(status().is(statusCode))
                .andExpect(jsonPath("$.code").value(errorCode));
    }

    private Cookie login(String email, String endpoint) throws Exception {
        MvcResult csrfResult = mockMvc.perform(get("/api/v1/auth/csrf"))
                .andExpect(status().isOk())
                .andReturn();
        Cookie csrfCookie = requireCookie(csrfResult, CSRF_COOKIE);
        JsonNode csrfJson = objectMapper.readTree(csrfResult.getResponse().getContentAsByteArray());

        String body = objectMapper.writeValueAsString(Map.of(
                "clientRequestId", "admin-order-auth-" + UUID.randomUUID(),
                "email", email,
                "password", "Password1"));
        MvcResult loginResult = mockMvc.perform(post(endpoint)
                        .cookie(csrfCookie)
                        .header(CSRF_HEADER, csrfJson.at("/data/token").asText())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andReturn();
        return requireCookie(loginResult, ACCESS_COOKIE);
    }

    private Cookie requireCookie(MvcResult result, String name) {
        Cookie cookie = result.getResponse().getCookie(name);
        assertThat(cookie).as("response cookie %s", name).isNotNull();
        return cookie;
    }

    private void insertUser(long id, String email, String role) {
        LocalDateTime now = LocalDateTime.of(2026, 8, 5, 18, 0);
        jdbcTemplate.update("""
                INSERT INTO sys_user (
                    id, email, password_hash, nickname, role_code, status, email_verified,
                    token_version, privacy_policy_version, privacy_accepted_at, version, create_time, update_time
                ) VALUES (?, ?, ?, ?, ?, 'NORMAL', 1, 0, '2026-08-03', ?, 0, ?, ?)
                """,
                id, email, passwordEncoder.encode("Password1"), role + "验收账号", role, now, now, now);
    }

    private void insertOrder() {
        LocalDateTime now = LocalDateTime.of(2026, 8, 5, 18, 0);
        jdbcTemplate.update("""
                INSERT INTO movie_show (
                    id, movie_id, cinema_id, auditorium_id, start_time, end_time,
                    language_version, base_price, data_type, source, status, version,
                    create_time, update_time
                ) VALUES (9201, 9301, 9401, 9501, ?, ?, '国语2D', 58.00,
                          'MOCK', 'auth-acceptance', 'ON_SALE', 0, ?, ?)
                """, now.plusDays(1), now.plusDays(1).plusHours(2), now, now);
        jdbcTemplate.update("""
                INSERT INTO ticket_order (
                    id, order_no, user_id, show_id, ticket_count, unit_price, total_amount,
                    status, expire_time, client_request_id, idempotency_key, version,
                    paid_time, cancelled_time, refunded_time, create_time, update_time
                ) VALUES (9601, 'CW-AUTH-ACCEPTANCE-1', 9101, 9201, 1, 58.00, 58.00,
                          'PENDING_PAYMENT', ?, 'auth-client-secret', 'auth-idempotency-secret', 0,
                          NULL, NULL, NULL, ?, ?)
                """, now.plusMinutes(15), now, now);
    }
}
