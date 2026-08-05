package com.miaoyu.ticket.auth.application;

import com.miaoyu.ticket.auth.domain.AccountStatus;
import com.miaoyu.ticket.auth.domain.AuthUser;
import com.miaoyu.ticket.auth.domain.RegistrationInvite;
import com.miaoyu.ticket.auth.domain.RegistrationInviteUse;
import com.miaoyu.ticket.auth.domain.VerificationPurpose;
import com.miaoyu.ticket.common.error.BusinessException;
import com.miaoyu.ticket.common.id.BusinessIdGenerator;
import java.time.Clock;
import java.time.LocalDateTime;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 注册的验证码、邀请码、账号和使用记录必须在同一个本地事务内提交或回滚。 */
@Service
public class RegistrationTransaction {

    private final AuthUserRepository userRepository;
    private final RegistrationInviteRepository inviteRepository;
    private final RegistrationInviteUseRepository inviteUseRepository;
    private final VerificationCodeVerifier verificationCodeVerifier;
    private final PasswordVerifier passwordVerifier;
    private final BusinessIdGenerator idGenerator;
    private final Clock clock;

    public RegistrationTransaction(
            AuthUserRepository userRepository,
            RegistrationInviteRepository inviteRepository,
            RegistrationInviteUseRepository inviteUseRepository,
            VerificationCodeVerifier verificationCodeVerifier,
            PasswordVerifier passwordVerifier,
            BusinessIdGenerator idGenerator,
            Clock clock) {
        this.userRepository = userRepository;
        this.inviteRepository = inviteRepository;
        this.inviteUseRepository = inviteUseRepository;
        this.verificationCodeVerifier = verificationCodeVerifier;
        this.passwordVerifier = passwordVerifier;
        this.idGenerator = idGenerator;
        this.clock = clock;
    }

    /** 先处理已完成请求，且必须重新核对原凭据，不能只凭请求编号返回账号。 */
    @Transactional
    public AuthUser execute(PreparedRegistration registration) {
        RegistrationInviteUse previous = inviteUseRepository
                .findByClientRequestId(registration.clientRequestId())
                .orElse(null);
        if (previous != null) {
            return recover(previous, registration);
        }

        if (userRepository.existsByEmail(registration.email())) {
            throw new BusinessException(AuthErrorCode.EMAIL_ALREADY_REGISTERED);
        }

        verificationCodeVerifier.verifyAndConsume(
                registration.email(), VerificationPurpose.REGISTER, registration.code());
        RegistrationInvite invite = inviteRepository
                .findByCodeHash(registration.inviteHash())
                .orElseThrow(this::inviteUnavailable);
        LocalDateTime now = LocalDateTime.now(clock);
        if (!inviteRepository.consume(invite.id(), invite.version(), now)) {
            throw inviteUnavailable();
        }

        long userId = idGenerator.nextId();
        String nickname = registration.nickname() == null
                ? "用户" + lastSixDigits(userId)
                : registration.nickname();
        AuthUser user = new AuthUser(
                userId,
                registration.email(),
                passwordVerifier.encode(registration.password()),
                nickname,
                RoleCode.USER,
                AccountStatus.NORMAL,
                true,
                0,
                registration.privacyPolicyVersion(),
                now);
        try {
            userRepository.create(user, now);
            inviteUseRepository.create(
                    new RegistrationInviteUse(
                            idGenerator.nextId(), invite.id(), userId, registration.clientRequestId(), now),
                    now);
        } catch (DuplicateKeyException exception) {
            throw new BusinessException(AuthErrorCode.EMAIL_ALREADY_REGISTERED);
        }
        return user;
    }

    private AuthUser recover(RegistrationInviteUse previous, PreparedRegistration registration) {
        AuthUser user = userRepository.findById(previous.userId()).orElseThrow(this::invalidReplay);
        RegistrationInvite invite = inviteRepository
                .findByCodeHash(registration.inviteHash())
                .orElseThrow(this::invalidReplay);
        boolean matches = user.email().equals(registration.email())
                && passwordVerifier.matches(registration.password(), user.passwordHash())
                && invite.id() == previous.inviteId()
                && user.privacyPolicyVersion().equals(registration.privacyPolicyVersion())
                && (registration.nickname() == null || registration.nickname().equals(user.nickname()));
        if (!matches) {
            throw invalidReplay();
        }
        return user;
    }

    private static String lastSixDigits(long userId) {
        String value = Long.toString(userId);
        return value.substring(Math.max(0, value.length() - 6));
    }

    private BusinessException inviteUnavailable() {
        return new BusinessException(AuthErrorCode.INVITE_CODE_UNAVAILABLE);
    }

    private BusinessException invalidReplay() {
        return new BusinessException(AuthErrorCode.INVALID_PARAMETER);
    }

    /** 已规范化、已完成请求级校验的事务输入。 */
    public record PreparedRegistration(
            String clientRequestId,
            String email,
            String code,
            String inviteHash,
            String password,
            String nickname,
            String privacyPolicyVersion) {
    }
}
