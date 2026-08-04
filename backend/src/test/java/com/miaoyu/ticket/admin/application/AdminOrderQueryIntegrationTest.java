package com.miaoyu.ticket.admin.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.miaoyu.ticket.auth.application.AuthErrorCode;
import com.miaoyu.ticket.auth.application.CurrentUser;
import com.miaoyu.ticket.auth.application.RoleCode;
import com.miaoyu.ticket.auth.application.UserAdminQueryPort;
import com.miaoyu.ticket.common.error.BusinessException;
import com.miaoyu.ticket.common.error.CommonErrorCode;
import com.miaoyu.ticket.order.application.OrderErrorCode;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(AdminOrderQueryIntegrationTest.AdminOrderTestConfiguration.class)
class AdminOrderQueryIntegrationTest {

    private static final long ADMIN_ID = 9001L;
    private static final long USER_A = 1001L;
    private static final long USER_B = 1002L;
    private static final long SHOW_A = 2001L;
    private static final long SHOW_B = 2002L;
    private static final LocalDateTime BASE_TIME = LocalDateTime.of(2026, 8, 4, 9, 0);

    @Autowired
    private AdminOrderQueryService queryService;

    @Autowired
    private FakeUserAdminQueryPort userQueryPort;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        clearTables();
        userQueryPort.reset();
        useRole(RoleCode.ADMIN);
        insertShow(SHOW_A, 501L, 601L, BASE_TIME.plusDays(1));
        insertShow(SHOW_B, 502L, 602L, BASE_TIME.plusDays(2));
        insertPaidOrder();
        insertRefundedOrder();
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void givenAdminAndCombinedFilters_whenQueryPage_thenReturnMaskedBatchSummary() {
        userQueryPort.match("refund@example", Set.of(USER_B));
        userQueryPort.summarize(USER_B, "r***@example.com");

        AdminOrderPageView result = queryService.queryOrders(new AdminOrderListQuery(
                null,
                " refund@example ",
                "REFUNDED",
                "502",
                Long.toString(SHOW_B),
                LocalDate.of(2026, 8, 4),
                LocalDate.of(2026, 8, 4),
                1,
                20));

        assertThat(result.total()).isEqualTo(1);
        assertThat(result.records()).singleElement().satisfies(order -> {
            assertThat(order.orderNo()).isEqualTo("CW-REFUNDED-1");
            assertThat(order.emailMasked()).isEqualTo("r***@example.com");
            assertThat(order.payment().status().name()).isEqualTo("SUCCESS");
            assertThat(order.ticket().status().name()).isEqualTo("REFUNDED");
            assertThat(order.refund().status().name()).isEqualTo("SUCCESS");
            assertThat(order.seats()).isEmpty();
        });
        assertThat(userQueryPort.lastKeyword()).isEqualTo("refund@example");
        assertThat(userQueryPort.lastSummaryIds()).containsExactly(USER_B);
    }

    @Test
    void givenRefundedOrder_whenQueryDetail_thenAggregateSeatPaymentTicketAndRefund() {
        userQueryPort.summarize(USER_B, "r***@example.com");

        AdminOrderView result = queryService.queryOrder(" CW-REFUNDED-1 ");

        assertThat(result.orderStatus().name()).isEqualTo("REFUNDED");
        assertThat(result.seats()).singleElement().satisfies(seat -> {
            assertThat(seat.rowNo()).isEqualTo("B");
            assertThat(seat.seatNo()).isEqualTo("08");
            assertThat(seat.unitPrice()).isEqualByComparingTo("68.00");
        });
        assertThat(result.payment().paymentNo()).isEqualTo("PAY-REFUNDED-1");
        assertThat(result.ticket().ticketCode()).isEqualTo("TICKET-REFUNDED-1");
        assertThat(result.refund().refundNo()).isEqualTo("REFUND-1");
    }

    @Test
    void givenMissingHistoricalUser_whenQuery_thenKeepOrderWithNullMaskedEmail() {
        AdminOrderView result = queryService.queryOrder("CW-PAID-1");

        assertThat(result.orderNo()).isEqualTo("CW-PAID-1");
        assertThat(result.emailMasked()).isNull();
    }

    @Test
    void givenNoMatchedUsers_whenQuery_thenReturnEmptyWithoutOrderLookupResult() {
        userQueryPort.match("nobody@example", Set.of());

        AdminOrderPageView result = queryService.queryOrders(new AdminOrderListQuery(
                null, "nobody@example", null, null, null, null, null, 1, 20));

        assertThat(result.total()).isZero();
        assertThat(result.records()).isEmpty();
        assertThat(userQueryPort.lastSummaryIds()).isEmpty();
    }

    @Test
    void givenUserDirectoryUnavailable_whenQuery_thenPropagate503Error() {
        userQueryPort.failWith(AuthErrorCode.USER_DIRECTORY_UNAVAILABLE);

        assertThatThrownBy(() -> queryService.queryOrder("CW-PAID-1"))
                .isInstanceOfSatisfying(BusinessException.class, exception -> {
                    assertThat(exception.getErrorCode()).isEqualTo(AuthErrorCode.USER_DIRECTORY_UNAVAILABLE);
                    assertThat(exception.getErrorCode().httpStatus().value()).isEqualTo(503);
                });
    }

    @Test
    void givenUserKeywordTooBroad_whenQuery_thenPropagateStable400Error() {
        userQueryPort.failWith(AuthErrorCode.USER_QUERY_TOO_BROAD);

        assertThatThrownBy(() -> queryService.queryOrders(new AdminOrderListQuery(
                null, "@example.com", null, null, null, null, null, 1, 20)))
                .isInstanceOfSatisfying(BusinessException.class, exception -> {
                    assertThat(exception.getErrorCode()).isEqualTo(AuthErrorCode.USER_QUERY_TOO_BROAD);
                    assertThat(exception.getErrorCode().httpStatus().value()).isEqualTo(400);
                });
    }

    @Test
    void givenNonAdmin_whenQuery_thenRejectBeforeUserDirectoryAndRepository() {
        useRole(RoleCode.USER);

        assertThatThrownBy(() -> queryService.queryOrder("CW-PAID-1"))
                .isInstanceOfSatisfying(BusinessException.class, exception -> assertThat(exception.getErrorCode())
                        .isEqualTo(CommonErrorCode.FORBIDDEN));
        assertThat(userQueryPort.lastSummaryIds()).isEmpty();
    }

    @Test
    void givenInvalidFilters_whenQuery_thenReturnStableParameterErrors() {
        assertThatThrownBy(() -> queryService.queryOrders(new AdminOrderListQuery(
                null, "   ", null, null, null, null, null, 1, 20)))
                .isInstanceOfSatisfying(BusinessException.class, exception -> assertThat(exception.getErrorCode())
                        .isEqualTo(AuthErrorCode.INVALID_PARAMETER));
        assertThatThrownBy(() -> queryService.queryOrders(new AdminOrderListQuery(
                null, null, "UNKNOWN", null, null, null, null, 1, 20)))
                .isInstanceOfSatisfying(BusinessException.class, exception -> assertThat(exception.getErrorCode())
                        .isEqualTo(CommonErrorCode.INVALID_PARAMETER));
        assertThatThrownBy(() -> queryService.queryOrders(new AdminOrderListQuery(
                null, null, null, "0", null, null, null, 1, 20)))
                .isInstanceOfSatisfying(BusinessException.class, exception -> assertThat(exception.getErrorCode())
                        .isEqualTo(CommonErrorCode.INVALID_PARAMETER));
        assertThatThrownBy(() -> queryService.queryOrders(new AdminOrderListQuery(
                null,
                null,
                null,
                null,
                null,
                LocalDate.of(2026, 8, 1),
                LocalDate.of(2026, 9, 1),
                1,
                20)))
                .isInstanceOfSatisfying(BusinessException.class, exception -> assertThat(exception.getErrorCode())
                        .isEqualTo(CommonErrorCode.INVALID_PARAMETER));
    }

    @Test
    void givenMaximumDateToHttpRequest_whenQueryAdminOrders_thenReturnStable400Error() throws Exception {
        mockMvc.perform(get("/api/v1/admin/orders")
                        .param("dateTo", "+999999999-12-31")
                        .with(authentication(authenticationFor(RoleCode.ADMIN)))
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(CommonErrorCode.INVALID_PARAMETER.code()));
    }

    @Test
    void givenMissingOrder_whenQueryDetail_thenReturnOrderNotFound() {
        assertThatThrownBy(() -> queryService.queryOrder("CW-NOT-FOUND"))
                .isInstanceOfSatisfying(BusinessException.class, exception -> assertThat(exception.getErrorCode())
                        .isEqualTo(OrderErrorCode.ORDER_NOT_FOUND));
    }

    @Test
    void givenAdminHttpRequest_whenQueryDetail_thenExcludeSensitiveInternalFields() throws Exception {
        userQueryPort.summarize(USER_B, "r***@example.com");

        mockMvc.perform(get("/api/v1/admin/orders/CW-REFUNDED-1")
                        .with(authentication(authenticationFor(RoleCode.ADMIN)))
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.summary.userId").value(Long.toString(USER_B)))
                .andExpect(jsonPath("$.data.summary.emailMasked").value("r***@example.com"))
                .andExpect(jsonPath("$.data.seats[0].seatId").value("4002"))
                .andExpect(content().string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString(
                        "refund-idempotency-secret"))))
                .andExpect(content().string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString(
                        "qr-payload-secret"))))
                .andExpect(content().string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString(
                        "impact-secret"))))
                .andExpect(content().string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString(
                        "action-secret"))));
    }

    @Test
    void givenNormalUserHttpRequest_whenQueryAdminOrders_thenReturn403() throws Exception {
        mockMvc.perform(get("/api/v1/admin/orders")
                        .with(authentication(authenticationFor(RoleCode.USER)))
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value(AuthErrorCode.FORBIDDEN.code()));
    }

    @Test
    void givenRepeatedReadQueries_whenExecute_thenKeepTransactionStateAndVersionsUnchanged() {
        userQueryPort.summarize(USER_B, "r***@example.com");
        Map<String, Object> before = transactionState(3002L);

        queryService.queryOrder("CW-REFUNDED-1");
        queryService.queryOrders(new AdminOrderListQuery(
                null, null, null, null, null, null, null, 1, 20));

        assertThat(transactionState(3002L)).containsExactlyInAnyOrderEntriesOf(before);
    }

    private void insertShow(long showId, long movieId, long cinemaId, LocalDateTime startTime) {
        jdbcTemplate.update("""
                INSERT INTO movie_show (
                    id, movie_id, cinema_id, auditorium_id, start_time, end_time,
                    language_version, base_price, data_type, source, status, version,
                    create_time, update_time
                ) VALUES (?, ?, ?, ?, ?, ?, '国语2D', 68.00, 'MOCK', 'admin-test', 'ON_SALE', 0, ?, ?)
                """, showId, movieId, cinemaId, showId + 100, startTime, startTime.plusHours(2), BASE_TIME, BASE_TIME);
    }

    private void insertPaidOrder() {
        insertOrder(3001L, "CW-PAID-1", USER_A, SHOW_A, "PAID", 58, 1, BASE_TIME.minusHours(2));
        jdbcTemplate.update("""
                INSERT INTO mock_payment (
                    id, payment_no, order_id, idempotency_key, amount, status,
                    request_time, paid_time, version, create_time, update_time
                ) VALUES (5001, 'PAY-PAID-1', 3001, 'payment-idempotency-paid', 58.00, 'SUCCESS', ?, ?, 1, ?, ?)
                """, BASE_TIME.minusHours(1), BASE_TIME.minusMinutes(50), BASE_TIME.minusHours(1), BASE_TIME);
        jdbcTemplate.update("""
                INSERT INTO electronic_ticket (
                    id, ticket_code, order_id, user_id, status, qr_payload, issued_time,
                    invalidated_time, version, create_time, update_time
                ) VALUES (6001, 'TICKET-PAID-1', 3001, ?, 'VALID', 'qr-paid-secret', ?, NULL, 0, ?, ?)
                """, USER_A, BASE_TIME.minusMinutes(50), BASE_TIME.minusMinutes(50), BASE_TIME);
    }

    private void insertRefundedOrder() {
        insertOrder(3002L, "CW-REFUNDED-1", USER_B, SHOW_B, "REFUNDED", 68, 3, BASE_TIME.minusHours(1));
        jdbcTemplate.update("""
                INSERT INTO ticket_order_seat (
                    id, order_id, show_seat_id, row_no_snapshot, seat_no_snapshot,
                    unit_price, create_time, update_time
                ) VALUES (7001, 3002, 4002, 'B', '08', 68.00, ?, ?)
                """, BASE_TIME.minusHours(1), BASE_TIME.minusHours(1));
        jdbcTemplate.update("""
                INSERT INTO mock_payment (
                    id, payment_no, order_id, idempotency_key, amount, status,
                    request_time, paid_time, version, create_time, update_time
                ) VALUES (5002, 'PAY-REFUNDED-1', 3002, 'payment-idempotency-refund', 68.00, 'SUCCESS', ?, ?, 1, ?, ?)
                """, BASE_TIME.minusMinutes(50), BASE_TIME.minusMinutes(40), BASE_TIME.minusMinutes(50), BASE_TIME);
        jdbcTemplate.update("""
                INSERT INTO electronic_ticket (
                    id, ticket_code, order_id, user_id, status, qr_payload, issued_time,
                    invalidated_time, version, create_time, update_time
                ) VALUES (6002, 'TICKET-REFUNDED-1', 3002, ?, 'REFUNDED', 'qr-payload-secret', ?, ?, 1, ?, ?)
                """, USER_B, BASE_TIME.minusMinutes(40), BASE_TIME.minusMinutes(10),
                BASE_TIME.minusMinutes(40), BASE_TIME);
        jdbcTemplate.update("""
                INSERT INTO refund_request (
                    id, refund_no, order_id, user_id, idempotency_key, action_id, reason,
                    impact_snapshot, status, request_time, processed_time, version,
                    create_time, update_time
                ) VALUES (8001, 'REFUND-1', 3002, ?, 'refund-idempotency-secret', 'action-secret',
                          '行程变化', '{"secret":"impact-secret"}', 'SUCCESS', ?, ?, 2, ?, ?)
                """, USER_B, BASE_TIME.minusMinutes(20), BASE_TIME.minusMinutes(10),
                BASE_TIME.minusMinutes(20), BASE_TIME);
    }

    private void insertOrder(
            long orderId,
            String orderNo,
            long userId,
            long showId,
            String status,
            int unitPrice,
            int version,
            LocalDateTime createTime) {
        jdbcTemplate.update("""
                INSERT INTO ticket_order (
                    id, order_no, user_id, show_id, ticket_count, unit_price, total_amount,
                    status, expire_time, client_request_id, idempotency_key, version,
                    paid_time, cancelled_time, refunded_time, create_time, update_time
                ) VALUES (?, ?, ?, ?, 1, ?, ?, ?, ?, ?, ?, ?, ?, NULL, ?, ?, ?)
                """,
                orderId,
                orderNo,
                userId,
                showId,
                BigDecimal.valueOf(unitPrice),
                BigDecimal.valueOf(unitPrice),
                status,
                createTime.plusMinutes(15),
                "client-secret-" + orderId,
                "order-idempotency-secret-" + orderId,
                version,
                status.equals("PAID") || status.equals("REFUNDED") ? createTime.plusMinutes(5) : null,
                status.equals("REFUNDED") ? BASE_TIME.minusMinutes(10) : null,
                createTime,
                BASE_TIME);
    }

    private Map<String, Object> transactionState(long orderId) {
        Map<String, Object> state = new LinkedHashMap<>();
        state.putAll(jdbcTemplate.queryForMap(
                "SELECT status AS order_status, version AS order_version FROM ticket_order WHERE id = ?",
                orderId));
        state.putAll(jdbcTemplate.queryForMap(
                "SELECT status AS payment_status, version AS payment_version FROM mock_payment WHERE order_id = ?",
                orderId));
        state.putAll(jdbcTemplate.queryForMap(
                "SELECT status AS ticket_status, version AS ticket_version FROM electronic_ticket WHERE order_id = ?",
                orderId));
        state.putAll(jdbcTemplate.queryForMap(
                "SELECT status AS refund_status, version AS refund_version FROM refund_request WHERE order_id = ?",
                orderId));
        return state;
    }

    private void clearTables() {
        jdbcTemplate.update("DELETE FROM refund_request");
        jdbcTemplate.update("DELETE FROM electronic_ticket");
        jdbcTemplate.update("DELETE FROM mock_payment");
        jdbcTemplate.update("DELETE FROM ticket_order_seat");
        jdbcTemplate.update("DELETE FROM ticket_order_operation");
        jdbcTemplate.update("DELETE FROM ticket_order");
        jdbcTemplate.update("DELETE FROM show_seat");
        jdbcTemplate.update("DELETE FROM movie_show");
    }

    private void useRole(RoleCode role) {
        SecurityContextHolder.getContext().setAuthentication(authenticationFor(role));
    }

    private UsernamePasswordAuthenticationToken authenticationFor(RoleCode role) {
        Collection<SimpleGrantedAuthority> authorities = Set.of(new SimpleGrantedAuthority("ROLE_" + role.name()));
        return UsernamePasswordAuthenticationToken.authenticated(
                new CurrentUser(role == RoleCode.ADMIN ? ADMIN_ID : USER_A, role, 0L),
                "N/A",
                authorities);
    }

    @TestConfiguration(proxyBeanMethods = false)
    static class AdminOrderTestConfiguration {

        @Bean
        @Primary
        FakeUserAdminQueryPort fakeUserAdminQueryPort() {
            return new FakeUserAdminQueryPort();
        }
    }

    static final class FakeUserAdminQueryPort implements UserAdminQueryPort {

        private final Map<String, Set<Long>> matches = new LinkedHashMap<>();
        private final Map<Long, UserAdminSummary> summaries = new LinkedHashMap<>();
        private String lastKeyword;
        private Set<Long> lastSummaryIds = Set.of();
        private AuthErrorCode failure;

        void reset() {
            matches.clear();
            summaries.clear();
            lastKeyword = null;
            lastSummaryIds = Set.of();
            failure = null;
        }

        void match(String keyword, Set<Long> userIds) {
            matches.put(keyword, Set.copyOf(userIds));
        }

        void summarize(long userId, String emailMasked) {
            summaries.put(userId, new UserAdminSummary(userId, emailMasked));
        }

        void failWith(AuthErrorCode errorCode) {
            failure = errorCode;
        }

        String lastKeyword() {
            return lastKeyword;
        }

        Set<Long> lastSummaryIds() {
            return lastSummaryIds;
        }

        @Override
        public Set<Long> findUserIdsByKeyword(String userKeyword) {
            failIfConfigured();
            lastKeyword = userKeyword;
            return matches.getOrDefault(userKeyword, Set.of());
        }

        @Override
        public Map<Long, UserAdminSummary> findByUserIds(Set<Long> userIds) {
            failIfConfigured();
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

        private void failIfConfigured() {
            if (failure != null) {
                throw new BusinessException(failure);
            }
        }
    }
}
