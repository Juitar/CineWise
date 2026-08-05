package com.miaoyu.ticket.auth.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.miaoyu.ticket.auth.domain.EmailVerificationCode;
import com.miaoyu.ticket.auth.domain.VerificationCodeStatus;
import com.miaoyu.ticket.auth.domain.VerificationPurpose;
import com.miaoyu.ticket.auth.infrastructure.config.VerificationCodeProperties;
import com.miaoyu.ticket.common.error.BusinessException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class VerificationCodeVerifierTest {

    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-08-05T02:00:00Z"), ZoneOffset.UTC);
    private final VerificationCodeRepository repository = mock(VerificationCodeRepository.class);
    private final VerificationCodeHasher hasher = mock(VerificationCodeHasher.class);
    private final FailedVerificationAttemptRecorder failedAttemptRecorder =
            mock(FailedVerificationAttemptRecorder.class);
    private VerificationCodeVerifier verifier;

    @BeforeEach
    void setUp() {
        verifier = new VerificationCodeVerifier(repository, hasher, failedAttemptRecorder, properties(), CLOCK);
    }

    @Test
    void shouldConsumeMatchingCodeOnce() {
        EmailVerificationCode candidate = candidate(1);
        when(repository.findLatestUsable(
                        "user@cinewise.test", VerificationPurpose.LOGIN, now(), 5))
                .thenReturn(Optional.of(candidate));
        when(hasher.matches("user@cinewise.test", VerificationPurpose.LOGIN, "123456", "stored-hash"))
                .thenReturn(true);
        when(repository.consume(10L, 1, now())).thenReturn(true);

        verifier.verifyAndConsume("user@cinewise.test", VerificationPurpose.LOGIN, "123456");

        verify(repository).consume(10L, 1, now());
        verify(failedAttemptRecorder, never()).record(10L, 1, 5, now());
    }

    @Test
    void shouldRecordFailedAttemptAndRejectMismatchedCode() {
        EmailVerificationCode candidate = candidate(4);
        when(repository.findLatestUsable(
                        "user@cinewise.test", VerificationPurpose.LOGIN, now(), 5))
                .thenReturn(Optional.of(candidate));

        assertThatThrownBy(() -> verifier.verifyAndConsume(
                        "user@cinewise.test", VerificationPurpose.LOGIN, "000000"))
                .isInstanceOfSatisfying(BusinessException.class, exception ->
                        assertThat(exception.getErrorCode()).isEqualTo(AuthErrorCode.VERIFICATION_CODE_INVALID));

        verify(failedAttemptRecorder).record(10L, 4, 5, now());
        verify(repository, never()).consume(10L, 4, now());
    }

    @Test
    void shouldRejectWhenConcurrentConsumeAlreadyWon() {
        EmailVerificationCode candidate = candidate(0);
        when(repository.findLatestUsable(
                        "user@cinewise.test", VerificationPurpose.LOGIN, now(), 5))
                .thenReturn(Optional.of(candidate));
        when(hasher.matches("user@cinewise.test", VerificationPurpose.LOGIN, "123456", "stored-hash"))
                .thenReturn(true);
        when(repository.consume(10L, 0, now())).thenReturn(false);

        assertThatThrownBy(() -> verifier.verifyAndConsume(
                        "user@cinewise.test", VerificationPurpose.LOGIN, "123456"))
                .isInstanceOf(BusinessException.class);
    }

    private EmailVerificationCode candidate(int attempts) {
        return new EmailVerificationCode(
                10L,
                "user@cinewise.test",
                VerificationPurpose.LOGIN,
                "stored-hash",
                VerificationCodeStatus.UNUSED,
                now().minusMinutes(1),
                now().plusMinutes(4),
                null,
                attempts);
    }

    private LocalDateTime now() {
        return LocalDateTime.now(CLOCK);
    }

    private VerificationCodeProperties properties() {
        return new VerificationCodeProperties(
                "test-verification-secret-at-least-32-bytes-long",
                6,
                Duration.ofMinutes(5),
                Duration.ofSeconds(60),
                5,
                Duration.ofMinutes(5),
                10,
                new VerificationCodeProperties.Mail(false, "", "验证码"));
    }
}
