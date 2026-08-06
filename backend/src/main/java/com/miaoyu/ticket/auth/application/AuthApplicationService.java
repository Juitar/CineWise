package com.miaoyu.ticket.auth.application;

import com.miaoyu.ticket.auth.domain.AuthUser;
import com.miaoyu.ticket.auth.domain.EmailAddress;
import com.miaoyu.ticket.auth.domain.LoginType;
import com.miaoyu.ticket.auth.domain.VerificationPurpose;
import com.miaoyu.ticket.common.error.BusinessException;
import com.miaoyu.ticket.common.id.BusinessIdGenerator;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 组织密码登录、当前用户查询和全设备登出，不把 HTTP 或 MyBatis 类型带入业务流程。 */
@Service
public class AuthApplicationService {

    private static final Logger LOGGER = LoggerFactory.getLogger(AuthApplicationService.class);

    private final AuthUserRepository userRepository;
    private final LoginAuditRepository auditRepository;
    private final PasswordVerifier passwordVerifier;
    private final AccessTokenService accessTokenService;
    private final LoginAuditSanitizer auditSanitizer;
    private final VerificationCodeVerifier verificationCodeVerifier;
    private final BusinessIdGenerator idGenerator;
    private final Clock clock;

    public AuthApplicationService(
            AuthUserRepository userRepository,
            LoginAuditRepository auditRepository,
            PasswordVerifier passwordVerifier,
            AccessTokenService accessTokenService,
            LoginAuditSanitizer auditSanitizer,
            VerificationCodeVerifier verificationCodeVerifier,
            BusinessIdGenerator idGenerator,
            Clock clock) {
        this.userRepository = userRepository;
        this.auditRepository = auditRepository;
        this.passwordVerifier = passwordVerifier;
        this.accessTokenService = accessTokenService;
        this.auditSanitizer = auditSanitizer;
        this.verificationCodeVerifier = verificationCodeVerifier;
        this.idGenerator = idGenerator;
        this.clock = clock;
    }

    /** 管理入口和用户入口共用凭据校验，但管理入口绝不把 USER 提升为 ADMIN。 */
    public LoginResult login(LoginCommand command) {
        String normalizedEmail = normalizeEmail(command);
        Optional<AuthUser> candidate = userRepository.findByEmail(normalizedEmail);
        AuthUser user = candidate.orElse(null);

        if (user == null || !passwordVerifier.matches(command.password(), user.passwordHash())) {
            audit(command, user == null ? null : user.id(), false, AuthErrorCode.INVALID_CREDENTIALS);
            throw new BusinessException(AuthErrorCode.INVALID_CREDENTIALS);
        }
        if (!user.isActive()) {
            audit(command, user.id(), false, AuthErrorCode.ACCOUNT_UNAVAILABLE);
            throw new BusinessException(AuthErrorCode.ACCOUNT_UNAVAILABLE);
        }
        if (command.loginType() == LoginType.ADMIN_PASSWORD && user.role() != RoleCode.ADMIN) {
            // 使用与错误密码相同的返回，避免管理入口泄露某邮箱属于普通用户。
            audit(command, user.id(), false, AuthErrorCode.INVALID_CREDENTIALS);
            throw new BusinessException(AuthErrorCode.INVALID_CREDENTIALS);
        }

        IssuedAccessToken token = accessTokenService.issue(user);
        audit(command, user.id(), true, null);
        return new LoginResult(token, toView(user));
    }

    /** 普通用户可使用一次性 LOGIN 验证码登录；管理员仍必须使用独立密码入口。 */
    public LoginResult loginWithEmailCode(EmailCodeLoginCommand command) {
        String normalizedEmail;
        try {
            normalizedEmail = EmailAddress.normalize(command.email());
        } catch (IllegalArgumentException exception) {
            audit(command, null, false, AuthErrorCode.INVALID_PARAMETER);
            throw new BusinessException(AuthErrorCode.INVALID_PARAMETER);
        }

        AuthUser user = userRepository.findByEmail(normalizedEmail).orElse(null);
        if (user == null || user.role() != RoleCode.USER) {
            audit(command, user == null ? null : user.id(), false, AuthErrorCode.VERIFICATION_CODE_INVALID);
            throw new BusinessException(AuthErrorCode.VERIFICATION_CODE_INVALID);
        }
        if (!user.isActive()) {
            audit(command, user.id(), false, AuthErrorCode.ACCOUNT_UNAVAILABLE);
            throw new BusinessException(AuthErrorCode.ACCOUNT_UNAVAILABLE);
        }
        if (!user.emailVerified()) {
            audit(command, user.id(), false, AuthErrorCode.VERIFICATION_CODE_INVALID);
            throw new BusinessException(AuthErrorCode.VERIFICATION_CODE_INVALID);
        }

        try {
            verificationCodeVerifier.verifyAndConsume(normalizedEmail, VerificationPurpose.LOGIN, command.code());
        } catch (BusinessException exception) {
            audit(command, user.id(), false, AuthErrorCode.VERIFICATION_CODE_INVALID);
            throw exception;
        }

        IssuedAccessToken token = accessTokenService.issue(user);
        audit(command, user.id(), true, null);
        return new LoginResult(token, toView(user));
    }

    /** 每次读取当前用户都回查账号，确保禁用和资料变化立即反映到响应。 */
    public CurrentUserView getCurrentUser(long userId) {
        AuthUser user = userRepository.findById(userId)
                .filter(AuthUser::isActive)
                .orElseThrow(() -> new BusinessException(AuthErrorCode.SESSION_INVALID));
        return toView(user);
    }

    /** 当前方案按账号递增 tokenVersion，因此一次登出会使该账号所有旧 JWT 失效。 */
    @Transactional
    public void logout(CurrentUser currentUser) {
        boolean updated = userRepository.incrementTokenVersion(
                currentUser.userId(), currentUser.tokenVersion(), LocalDateTime.now(clock));
        if (!updated) {
            LOGGER.debug("登出时会话版本已变化, userId={}", currentUser.userId());
        }
    }

    private String normalizeEmail(LoginCommand command) {
        try {
            return EmailAddress.normalize(command.email());
        } catch (IllegalArgumentException exception) {
            audit(command, null, false, AuthErrorCode.INVALID_PARAMETER);
            throw new BusinessException(AuthErrorCode.INVALID_PARAMETER);
        }
    }

    private CurrentUserView toView(AuthUser user) {
        return new CurrentUserView(
                Long.toString(user.id()),
                user.role(),
                user.nickname(),
                EmailAddress.mask(user.email()),
                user.emailVerified(),
                user.status(),
                user.privacyPolicyVersion());
    }

    /** 审计采用独立事务；数据库或摘要失败只能告警，不能改变登录成败。 */
    private void audit(LoginCommand command, Long userId, boolean success, AuthErrorCode failureCode) {
        audit(
                command.loginType(),
                command.remoteAddress(),
                command.userAgent(),
                command.traceId(),
                userId,
                success,
                failureCode);
    }

    private void audit(EmailCodeLoginCommand command, Long userId, boolean success, AuthErrorCode failureCode) {
        audit(
                LoginType.EMAIL_CODE,
                command.remoteAddress(),
                command.userAgent(),
                command.traceId(),
                userId,
                success,
                failureCode);
    }

    private void audit(
            LoginType loginType,
            String remoteAddress,
            String userAgent,
            String traceId,
            Long userId,
            boolean success,
            AuthErrorCode failureCode) {
        try {
            auditRepository.append(new LoginAuditRepository.LoginAuditRecord(
                    idGenerator.nextId(),
                    userId,
                    loginType,
                    success,
                    failureCode == null ? null : Integer.toString(failureCode.code()),
                    auditSanitizer.hashIp(remoteAddress),
                    auditSanitizer.summarizeUserAgent(userAgent),
                    traceId,
                    LocalDateTime.now(clock)));
        } catch (RuntimeException exception) {
            LOGGER.warn("登录审计写入失败, traceId={}", traceId, exception);
        }
    }
}
