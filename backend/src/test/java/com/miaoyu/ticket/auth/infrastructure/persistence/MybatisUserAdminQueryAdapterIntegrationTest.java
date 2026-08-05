package com.miaoyu.ticket.auth.infrastructure.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.miaoyu.ticket.auth.application.AuthErrorCode;
import com.miaoyu.ticket.auth.application.CurrentUser;
import com.miaoyu.ticket.auth.application.RoleCode;
import com.miaoyu.ticket.auth.application.UserAdminQueryPort;
import com.miaoyu.ticket.common.error.BusinessException;
import java.time.LocalDateTime;
import java.util.Set;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest
@ActiveProfiles("test")
class MybatisUserAdminQueryAdapterIntegrationTest {

    @Autowired
    private UserAdminQueryPort userAdminQueryPort;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @BeforeEach
    void setUp() {
        jdbcTemplate.update("DELETE FROM sys_login_log");
        jdbcTemplate.update("DELETE FROM sys_user");
        useAdmin();
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void shouldUseLiteralLikeCharactersAndReturnMaskedExistingUsers() {
        insertUser(1001L, "percent%@example.com");
        insertUser(1002L, "under_score@example.com");
        insertUser(1003L, "ordinary@example.com");

        assertThat(userAdminQueryPort.findUserIdsByKeyword("%@example")).containsExactly(1001L);
        assertThat(userAdminQueryPort.findUserIdsByKeyword("under_")).containsExactly(1002L);
        assertThat(userAdminQueryPort.findUserIdsByKeyword("\\x")).isEmpty();
        assertThat(userAdminQueryPort.findUserIdsByKeyword("1003")).containsExactly(1003L);
        var summaries = userAdminQueryPort.findByUserIds(Set.of(1001L, 9999L));
        assertThat(summaries).containsOnlyKeys(1001L);
        assertThat(summaries.get(1001L).emailMasked()).isEqualTo("p***@example.com");
    }

    @Test
    void shouldRejectEmailKeywordWhenTheOneHundredFirstUserMatches() {
        for (long userId = 1L; userId <= 101L; userId++) {
            insertUser(userId, "member" + userId + "@example.com");
        }

        assertThatThrownBy(() -> userAdminQueryPort.findUserIdsByKeyword("member"))
                .isInstanceOfSatisfying(BusinessException.class, exception -> assertThat(exception.getErrorCode())
                        .isEqualTo(AuthErrorCode.USER_QUERY_TOO_BROAD));
    }

    private void useAdmin() {
        CurrentUser currentUser = new CurrentUser(9001L, RoleCode.ADMIN, 0L);
        SecurityContextHolder.getContext().setAuthentication(UsernamePasswordAuthenticationToken.authenticated(
                currentUser,
                "N/A",
                Set.of(new SimpleGrantedAuthority("ROLE_ADMIN"))));
    }

    private void insertUser(long userId, String email) {
        LocalDateTime now = LocalDateTime.of(2026, 8, 5, 18, 0);
        jdbcTemplate.update("""
                INSERT INTO sys_user (
                    id, email, password_hash, nickname, role_code, status, email_verified,
                    token_version, privacy_policy_version, privacy_accepted_at, version, create_time, update_time
                ) VALUES (?, ?, 'not-used', '目录测试账号', 'USER', 'NORMAL', 1,
                          0, '2026-08-03', ?, 0, ?, ?)
                """, userId, email, now, now, now);
    }
}
