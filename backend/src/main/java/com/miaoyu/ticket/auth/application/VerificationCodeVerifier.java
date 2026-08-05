package com.miaoyu.ticket.auth.application;

import com.miaoyu.ticket.auth.domain.EmailVerificationCode;
import com.miaoyu.ticket.auth.domain.VerificationPurpose;
import com.miaoyu.ticket.auth.infrastructure.config.VerificationCodeProperties;
import com.miaoyu.ticket.common.error.BusinessException;
import java.time.Clock;
import java.time.LocalDateTime;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 校验失败次数和一次性消费都通过数据库条件更新裁决。 */
@Service
public class VerificationCodeVerifier {

    private final VerificationCodeRepository repository;
    private final VerificationCodeHasher hasher;
    private final FailedVerificationAttemptRecorder failedAttemptRecorder;
    private final VerificationCodeProperties properties;
    private final Clock clock;

    public VerificationCodeVerifier(
            VerificationCodeRepository repository,
            VerificationCodeHasher hasher,
            FailedVerificationAttemptRecorder failedAttemptRecorder,
            VerificationCodeProperties properties,
            Clock clock) {
        this.repository = repository;
        this.hasher = hasher;
        this.failedAttemptRecorder = failedAttemptRecorder;
        this.properties = properties;
        this.clock = clock;
    }

    @Transactional
    public void verifyAndConsume(String normalizedEmail, VerificationPurpose purpose, String code) {
        LocalDateTime now = LocalDateTime.now(clock);
        EmailVerificationCode candidate = repository
                .findLatestUsable(normalizedEmail, purpose, now, properties.maximumAttempts())
                .orElseThrow(this::invalidCode);

        if (!hasher.matches(normalizedEmail, purpose, code, candidate.codeHash())) {
            failedAttemptRecorder.record(
                    candidate.id(),
                    candidate.attemptCount(),
                    properties.maximumAttempts(),
                    now);
            throw invalidCode();
        }

        boolean consumed = repository.consume(candidate.id(), candidate.attemptCount(), now);
        if (!consumed) {
            throw invalidCode();
        }
    }

    private BusinessException invalidCode() {
        return new BusinessException(AuthErrorCode.VERIFICATION_CODE_INVALID);
    }
}
