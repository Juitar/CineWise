package com.miaoyu.ticket.auth.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.miaoyu.ticket.auth.domain.AccountStatus;
import com.miaoyu.ticket.auth.domain.AuthUser;
import com.miaoyu.ticket.auth.domain.LoginType;
import com.miaoyu.ticket.auth.domain.VerificationPurpose;
import com.miaoyu.ticket.common.error.BusinessException;
import com.miaoyu.ticket.common.id.BusinessIdGenerator;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class AuthApplicationServiceTest {

    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-08-03T08:00:00Z"), ZoneOffset.UTC);
    private final AuthUserRepository userRepository = mock(AuthUserRepository.class);
    private final LoginAuditRepository auditRepository = mock(LoginAuditRepository.class);
    private final PasswordVerifier passwordVerifier = mock(PasswordVerifier.class);
    private final AccessTokenService tokenService = mock(AccessTokenService.class);
    private final LoginAuditSanitizer sanitizer = mock(LoginAuditSanitizer.class);
    private final VerificationCodeVerifier verificationCodeVerifier = mock(VerificationCodeVerifier.class);
    private final BusinessIdGenerator idGenerator = () -> 9001L;
    private AuthApplicationService service;

    @BeforeEach
    void setUp() {
        service = new AuthApplicationService(
                userRepository,
                auditRepository,
                passwordVerifier,
                tokenService,
                sanitizer,
                verificationCodeVerifier,
                idGenerator,
                CLOCK);
    }

    @Test
    void shouldLoginUserAndReturnMaskedView() {
        AuthUser user = user(RoleCode.USER, AccountStatus.NORMAL);
        when(userRepository.findByEmail("user@cinewise.test")).thenReturn(Optional.of(user));
        when(passwordVerifier.matches("Password1", user.passwordHash())).thenReturn(true);
        when(tokenService.issue(user)).thenReturn("signed-token");

        LoginResult result = service.login(command(LoginType.PASSWORD));

        assertThat(result.accessToken()).isEqualTo("signed-token");
        assertThat(result.currentUser().id()).isEqualTo("1001");
        assertThat(result.currentUser().emailMasked()).isEqualTo("u***@cinewise.test");
        assertThat(result.currentUser().role()).isEqualTo(RoleCode.USER);
        verify(auditRepository).append(any(LoginAuditRepository.LoginAuditRecord.class));
    }

    @Test
    void shouldRejectUserFromAdminLoginWithoutIssuingToken() {
        AuthUser user = user(RoleCode.USER, AccountStatus.NORMAL);
        when(userRepository.findByEmail("user@cinewise.test")).thenReturn(Optional.of(user));
        when(passwordVerifier.matches("Password1", user.passwordHash())).thenReturn(true);

        assertThatThrownBy(() -> service.login(command(LoginType.ADMIN_PASSWORD)))
                .isInstanceOfSatisfying(BusinessException.class, exception ->
                        assertThat(exception.getErrorCode()).isEqualTo(AuthErrorCode.INVALID_CREDENTIALS));
    }

    @Test
    void shouldReturnAccountUnavailableForDisabledUser() {
        AuthUser user = user(RoleCode.USER, AccountStatus.DISABLED);
        when(userRepository.findByEmail("user@cinewise.test")).thenReturn(Optional.of(user));
        when(passwordVerifier.matches("Password1", user.passwordHash())).thenReturn(true);

        assertThatThrownBy(() -> service.login(command(LoginType.PASSWORD)))
                .isInstanceOfSatisfying(BusinessException.class, exception ->
                        assertThat(exception.getErrorCode()).isEqualTo(AuthErrorCode.ACCOUNT_UNAVAILABLE));
    }

    @Test
    void shouldKeepSuccessfulLoginWhenAuditInsertFails() {
        AuthUser user = user(RoleCode.USER, AccountStatus.NORMAL);
        when(userRepository.findByEmail("user@cinewise.test")).thenReturn(Optional.of(user));
        when(passwordVerifier.matches("Password1", user.passwordHash())).thenReturn(true);
        when(tokenService.issue(user)).thenReturn("signed-token");
        doThrow(new IllegalStateException("audit unavailable"))
                .when(auditRepository).append(any(LoginAuditRepository.LoginAuditRecord.class));

        assertThat(service.login(command(LoginType.PASSWORD)).accessToken()).isEqualTo("signed-token");
    }

    @Test
    void shouldLoginNormalUserWithEmailCode() {
        AuthUser user = user(RoleCode.USER, AccountStatus.NORMAL);
        when(userRepository.findByEmail("user@cinewise.test")).thenReturn(Optional.of(user));
        when(tokenService.issue(user)).thenReturn("email-code-token");

        LoginResult result = service.loginWithEmailCode(emailCodeCommand());

        assertThat(result.accessToken()).isEqualTo("email-code-token");
        verify(verificationCodeVerifier)
                .verifyAndConsume("user@cinewise.test", VerificationPurpose.LOGIN, "123456");
        verify(auditRepository).append(any(LoginAuditRepository.LoginAuditRecord.class));
    }

    @Test
    void shouldRejectAdminFromEmailCodeLoginWithoutConsumingCode() {
        AuthUser admin = user(RoleCode.ADMIN, AccountStatus.NORMAL);
        when(userRepository.findByEmail("user@cinewise.test")).thenReturn(Optional.of(admin));

        assertThatThrownBy(() -> service.loginWithEmailCode(emailCodeCommand()))
                .isInstanceOfSatisfying(BusinessException.class, exception ->
                        assertThat(exception.getErrorCode()).isEqualTo(AuthErrorCode.VERIFICATION_CODE_INVALID));

        verify(verificationCodeVerifier, org.mockito.Mockito.never())
                .verifyAndConsume(any(), any(), any());
    }

    private LoginCommand command(LoginType type) {
        return new LoginCommand(
                "request-1", " User@CineWise.Test ", "Password1", type, "127.0.0.1", "JUnit", "trace-1");
    }

    private AuthUser user(RoleCode role, AccountStatus status) {
        return new AuthUser(
                1001L,
                "user@cinewise.test",
                "$2a$10$hash",
                "测试用户",
                role,
                status,
                true,
                3L,
                "2026-08-03",
                LocalDateTime.of(2026, 8, 3, 8, 0));
    }

    private EmailCodeLoginCommand emailCodeCommand() {
        return new EmailCodeLoginCommand(
                "request-email-1", " User@CineWise.Test ", "123456", "127.0.0.1", "JUnit", "trace-email-1");
    }
}
