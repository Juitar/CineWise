package com.miaoyu.ticket.auth.application;

import com.miaoyu.ticket.auth.domain.AuthUser;
import com.miaoyu.ticket.auth.domain.EmailAddress;
import com.miaoyu.ticket.auth.domain.LoginType;
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
    private final BusinessIdGenerator idGenerator;
    private final Clock clock;

    public AuthApplicationService(
            AuthUserRepository userRepository,
            LoginAuditRepository auditRepository,
            PasswordVerifier passwordVerifier,
            AccessTokenService accessTokenService,
            LoginAuditSanitizer auditSanitizer,
            BusinessIdGenerator idGenerator,
            Clock clock) {
        this.userRepository = userRepository;
        this.auditRepository = auditRepository;
        this.passwordVerifier = passwordVerifier;
        this.accessTokenService = accessTokenService;
        this.auditSanitizer = auditSanitizer;
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

        String token = accessTokenService.issue(user);
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
        try {
            auditRepository.append(new LoginAuditRepository.LoginAuditRecord(
                    idGenerator.nextId(),
                    userId,
                    command.loginType(),
                    success,
                    failureCode == null ? null : Integer.toString(failureCode.code()),
                    auditSanitizer.hashIp(command.remoteAddress()),
                    auditSanitizer.summarizeUserAgent(command.userAgent()),
                    command.traceId(),
                    LocalDateTime.now(clock)));
        } catch (RuntimeException exception) {
            LOGGER.warn("登录审计写入失败, traceId={}", command.traceId(), exception);
        }
    }
}
