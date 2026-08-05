package com.miaoyu.ticket.auth.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.miaoyu.ticket.auth.domain.AccountStatus;
import com.miaoyu.ticket.auth.domain.AuthUser;
import com.miaoyu.ticket.auth.domain.EmailVerificationCode;
import com.miaoyu.ticket.auth.domain.VerificationCodeStatus;
import com.miaoyu.ticket.auth.domain.VerificationPurpose;
import com.miaoyu.ticket.auth.infrastructure.config.VerificationCodeProperties;
import com.miaoyu.ticket.common.error.BusinessException;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class EmailCodeApplicationServiceTest {

    private final AuthUserRepository userRepository = mock(AuthUserRepository.class);
    private final VerificationCodeGenerator generator = mock(VerificationCodeGenerator.class);
    private final VerificationCodeHasher hasher = mock(VerificationCodeHasher.class);
    private final VerificationCodeRateLimiter rateLimiter = mock(VerificationCodeRateLimiter.class);
    private final VerificationCodeIssueTransaction issueTransaction = mock(VerificationCodeIssueTransaction.class);
    private final VerificationEmailSender sender = mock(VerificationEmailSender.class);
    private final LoginAuditSanitizer sanitizer = mock(LoginAuditSanitizer.class);
    private EmailCodeApplicationService service;

    @BeforeEach
    void setUp() {
        service = new EmailCodeApplicationService(
                userRepository,
                generator,
                hasher,
                rateLimiter,
                issueTransaction,
                sender,
                sanitizer,
                properties());
        when(hasher.hash("user@cinewise.test", VerificationPurpose.LOGIN, "rate-limit"))
                .thenReturn("email-hash");
        when(sanitizer.hashIp("127.0.0.1")).thenReturn("ip-hash");
    }

    @Test
    void shouldIssueAndSendLoginCodeForNormalUser() {
        when(rateLimiter.acquire(any(), any(), any(), any(), any(), anyInt()))
                .thenReturn(new VerificationCodeRateLimiter.SendPermit(true, true, 60));
        when(userRepository.findByEmail("user@cinewise.test")).thenReturn(Optional.of(user()));
        when(generator.generate()).thenReturn("123456");
        when(hasher.hash("user@cinewise.test", VerificationPurpose.LOGIN, "123456"))
                .thenReturn("code-hash");
        when(issueTransaction.issue(
                        "user@cinewise.test", VerificationPurpose.LOGIN, "code-hash", Duration.ofMinutes(5)))
                .thenReturn(stored());
        when(sender.send("user@cinewise.test", "123456", VerificationPurpose.LOGIN, "trace-1"))
                .thenReturn(VerificationEmailSender.DeliveryResult.SENT);

        SendEmailCodeResult result = service.send(command());

        assertThat(result).isEqualTo(new SendEmailCodeResult(60, 300));
        verify(sender).send("user@cinewise.test", "123456", VerificationPurpose.LOGIN, "trace-1");
    }

    @Test
    void shouldReturnRemainingCooldownWithoutSecondIssue() {
        when(rateLimiter.acquire(any(), any(), any(), any(), any(), anyInt()))
                .thenReturn(new VerificationCodeRateLimiter.SendPermit(false, true, 42));

        assertThat(service.send(command())).isEqualTo(new SendEmailCodeResult(42, 300));

        verify(userRepository, never()).findByEmail(any());
        verify(sender, never()).send(any(), any(), any(), any());
    }

    @Test
    void shouldHideUnknownLoginEmailBehindSuccessResponse() {
        when(rateLimiter.acquire(any(), any(), any(), any(), any(), anyInt()))
                .thenReturn(new VerificationCodeRateLimiter.SendPermit(true, true, 60));
        when(userRepository.findByEmail("user@cinewise.test")).thenReturn(Optional.empty());

        assertThat(service.send(command())).isEqualTo(new SendEmailCodeResult(60, 300));

        verify(issueTransaction, never()).issue(any(), any(), any(), any());
        verify(sender, never()).send(any(), any(), any(), any());
    }

    @Test
    void shouldInvalidateAndReleaseCooldownOnDefiniteMailFailure() {
        when(rateLimiter.acquire(any(), any(), any(), any(), any(), anyInt()))
                .thenReturn(new VerificationCodeRateLimiter.SendPermit(true, true, 60));
        when(userRepository.findByEmail("user@cinewise.test")).thenReturn(Optional.of(user()));
        when(generator.generate()).thenReturn("123456");
        when(hasher.hash("user@cinewise.test", VerificationPurpose.LOGIN, "123456"))
                .thenReturn("code-hash");
        when(issueTransaction.issue(any(), any(), any(), any())).thenReturn(stored());
        when(sender.send(any(), any(), any(), any()))
                .thenReturn(VerificationEmailSender.DeliveryResult.FAILED);

        assertThatThrownBy(() -> service.send(command()))
                .isInstanceOfSatisfying(BusinessException.class, exception ->
                        assertThat(exception.getErrorCode()).isEqualTo(AuthErrorCode.MAIL_SERVICE_UNAVAILABLE));

        verify(issueTransaction).invalidate(99L);
        verify(rateLimiter).releaseEmailCooldown("email-hash", VerificationPurpose.LOGIN);
    }

    private SendEmailCodeCommand command() {
        return new SendEmailCodeCommand(
                " User@CineWise.Test ", VerificationPurpose.LOGIN, "127.0.0.1", "trace-1");
    }

    private AuthUser user() {
        return new AuthUser(
                1001L,
                "user@cinewise.test",
                "hash",
                "测试用户",
                RoleCode.USER,
                AccountStatus.NORMAL,
                true,
                0,
                "2026-08-03",
                LocalDateTime.of(2026, 8, 3, 8, 0));
    }

    private EmailVerificationCode stored() {
        return new EmailVerificationCode(
                99L,
                "user@cinewise.test",
                VerificationPurpose.LOGIN,
                "code-hash",
                VerificationCodeStatus.UNUSED,
                LocalDateTime.of(2026, 8, 5, 10, 0),
                LocalDateTime.of(2026, 8, 5, 10, 5),
                null,
                0);
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
