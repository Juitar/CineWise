package com.miaoyu.ticket.auth.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.miaoyu.ticket.auth.domain.AccountStatus;
import com.miaoyu.ticket.auth.domain.AuthUser;
import com.miaoyu.ticket.auth.domain.VerificationPurpose;
import com.miaoyu.ticket.common.error.BusinessException;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class PasswordResetTransactionTest {

    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-08-06T01:00:00Z"), ZoneOffset.UTC);

    private final AuthUserRepository repository = mock(AuthUserRepository.class);
    private final VerificationCodeVerifier verifier = mock(VerificationCodeVerifier.class);
    private final PasswordVerifier passwordVerifier = mock(PasswordVerifier.class);
    private final PasswordResetTransaction transaction =
            new PasswordResetTransaction(repository, verifier, passwordVerifier, CLOCK);

    @Test
    void shouldConsumeResetPurposeAndAtomicallyUpdatePasswordVersion() {
        when(repository.findByEmail("user@cinewise.test")).thenReturn(Optional.of(user()));
        when(passwordVerifier.encode("NewPassword1")).thenReturn("new-hash");
        when(repository.resetPassword(1001L, 3L, "new-hash", LocalDateTime.now(CLOCK)))
                .thenReturn(true);

        transaction.execute("user@cinewise.test", "123456", "NewPassword1");

        verify(verifier).verifyAndConsume(
                "user@cinewise.test", VerificationPurpose.RESET_PASSWORD, "123456");
        verify(repository).resetPassword(1001L, 3L, "new-hash", LocalDateTime.now(CLOCK));
    }

    @Test
    void shouldHideUnavailableUserAsInvalidCode() {
        when(repository.findByEmail("missing@cinewise.test")).thenReturn(Optional.empty());

        assertInvalid(() -> transaction.execute("missing@cinewise.test", "123456", "NewPassword1"));
    }

    @Test
    void shouldFailWhenConcurrentAccountUpdateWins() {
        when(repository.findByEmail("user@cinewise.test")).thenReturn(Optional.of(user()));
        when(passwordVerifier.encode("NewPassword1")).thenReturn("new-hash");
        when(repository.resetPassword(1001L, 3L, "new-hash", LocalDateTime.now(CLOCK)))
                .thenReturn(false);

        assertInvalid(() -> transaction.execute("user@cinewise.test", "123456", "NewPassword1"));
    }

    private void assertInvalid(Runnable action) {
        assertThatThrownBy(action::run)
                .isInstanceOfSatisfying(BusinessException.class, exception ->
                        assertThat(exception.getErrorCode()).isEqualTo(AuthErrorCode.VERIFICATION_CODE_INVALID));
    }

    private AuthUser user() {
        return new AuthUser(
                1001L,
                "user@cinewise.test",
                "old-hash",
                "测试用户",
                RoleCode.USER,
                AccountStatus.NORMAL,
                true,
                3L,
                "2026-08-03",
                LocalDateTime.of(2026, 8, 3, 8, 0));
    }
}
