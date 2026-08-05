package com.miaoyu.ticket.auth.application;

import com.miaoyu.ticket.auth.domain.EmailVerificationCode;
import com.miaoyu.ticket.auth.domain.VerificationPurpose;
import java.time.LocalDateTime;
import java.util.Optional;

/** 验证码持久化只暴露发送、失败尝试和一次性条件消费所需操作。 */
public interface VerificationCodeRepository {

    void invalidateActive(String normalizedEmail, VerificationPurpose purpose, LocalDateTime updateTime);

    void create(EmailVerificationCode code, LocalDateTime createTime);

    Optional<EmailVerificationCode> findLatestUsable(
            String normalizedEmail, VerificationPurpose purpose, LocalDateTime now, int maximumAttempts);

    boolean consume(long id, int expectedAttemptCount, LocalDateTime usedTime);

    boolean recordFailedAttempt(
            long id, int expectedAttemptCount, int maximumAttempts, LocalDateTime updateTime);

    boolean invalidate(long id, LocalDateTime updateTime);
}
