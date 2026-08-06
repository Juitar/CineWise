package com.miaoyu.ticket.auth.application;

import com.miaoyu.ticket.auth.domain.AuthUser;
import com.miaoyu.ticket.auth.domain.VerificationPurpose;
import com.miaoyu.ticket.common.error.BusinessException;
import java.time.Clock;
import java.time.LocalDateTime;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 验证码消费、密码摘要和会话版本只能整体提交，任何失败都不得留下半完成数据。 */
@Service
public class PasswordResetTransaction {

    private final AuthUserRepository userRepository;
    private final VerificationCodeVerifier verificationCodeVerifier;
    private final PasswordVerifier passwordVerifier;
    private final Clock clock;

    public PasswordResetTransaction(
            AuthUserRepository userRepository,
            VerificationCodeVerifier verificationCodeVerifier,
            PasswordVerifier passwordVerifier,
            Clock clock) {
        this.userRepository = userRepository;
        this.verificationCodeVerifier = verificationCodeVerifier;
        this.passwordVerifier = passwordVerifier;
        this.clock = clock;
    }

    @Transactional
    public void execute(String normalizedEmail, String code, String newPassword) {
        AuthUser user = userRepository
                .findByEmail(normalizedEmail)
                .filter(AuthUser::isActive)
                .filter(AuthUser::emailVerified)
                .filter(candidate -> candidate.role() == RoleCode.USER)
                .orElseThrow(this::invalidCode);

        verificationCodeVerifier.verifyAndConsume(
                normalizedEmail, VerificationPurpose.RESET_PASSWORD, code);
        String passwordHash = passwordVerifier.encode(newPassword);
        boolean updated = userRepository.resetPassword(
                user.id(), user.tokenVersion(), passwordHash, LocalDateTime.now(clock));
        if (!updated) {
            throw invalidCode();
        }
    }

    private BusinessException invalidCode() {
        return new BusinessException(AuthErrorCode.VERIFICATION_CODE_INVALID);
    }
}
