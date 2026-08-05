package com.miaoyu.ticket.auth.application;

import com.miaoyu.ticket.auth.domain.AuthUser;
import com.miaoyu.ticket.auth.domain.EmailAddress;
import com.miaoyu.ticket.auth.infrastructure.config.RegistrationProperties;
import com.miaoyu.ticket.common.error.BusinessException;
import org.springframework.stereotype.Service;

/** 注册应用服务负责请求规则和会话签发，数据库写入交给单一事务组件。 */
@Service
public class RegistrationApplicationService {

    private final RegistrationTransaction transaction;
    private final RegistrationInviteHasher inviteHasher;
    private final RegistrationProperties properties;
    private final AccessTokenService accessTokenService;

    public RegistrationApplicationService(
            RegistrationTransaction transaction,
            RegistrationInviteHasher inviteHasher,
            RegistrationProperties properties,
            AccessTokenService accessTokenService) {
        this.transaction = transaction;
        this.inviteHasher = inviteHasher;
        this.properties = properties;
        this.accessTokenService = accessTokenService;
    }

    public LoginResult register(RegistrationCommand command) {
        if (!command.privacyAccepted()
                || !properties.currentPrivacyPolicyVersion().equals(command.privacyPolicyVersion())) {
            throw new BusinessException(AuthErrorCode.PRIVACY_POLICY_INVALID);
        }

        String email;
        try {
            email = EmailAddress.normalize(command.email());
        } catch (IllegalArgumentException exception) {
            throw new BusinessException(AuthErrorCode.INVALID_PARAMETER);
        }
        String nickname = normalizeNickname(command.nickname());
        AuthUser user = transaction.execute(new RegistrationTransaction.PreparedRegistration(
                command.clientRequestId(),
                email,
                command.code(),
                inviteHasher.hash(command.inviteCode()),
                command.password(),
                nickname,
                command.privacyPolicyVersion()));
        return new LoginResult(accessTokenService.issue(user), toView(user));
    }

    private String normalizeNickname(String nickname) {
        if (nickname == null || nickname.isBlank()) {
            return null;
        }
        String normalized = nickname.strip();
        if (normalized.length() > 64) {
            throw new BusinessException(AuthErrorCode.INVALID_PARAMETER);
        }
        return normalized;
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
}
