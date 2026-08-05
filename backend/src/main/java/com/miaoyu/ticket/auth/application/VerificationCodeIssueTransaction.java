package com.miaoyu.ticket.auth.application;

import com.miaoyu.ticket.auth.domain.EmailVerificationCode;
import com.miaoyu.ticket.auth.domain.VerificationCodeStatus;
import com.miaoyu.ticket.auth.domain.VerificationPurpose;
import com.miaoyu.ticket.common.id.BusinessIdGenerator;
import java.time.Clock;
import java.time.Duration;
import java.time.LocalDateTime;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/** 验证码登记和失效使用短事务，外部邮件发送不进入数据库事务。 */
@Service
public class VerificationCodeIssueTransaction {

    private final VerificationCodeRepository repository;
    private final BusinessIdGenerator idGenerator;
    private final Clock clock;

    public VerificationCodeIssueTransaction(
            VerificationCodeRepository repository, BusinessIdGenerator idGenerator, Clock clock) {
        this.repository = repository;
        this.idGenerator = idGenerator;
        this.clock = clock;
    }

    @Transactional
    public EmailVerificationCode issue(
            String normalizedEmail,
            VerificationPurpose purpose,
            String codeHash,
            Duration timeToLive) {
        LocalDateTime now = LocalDateTime.now(clock);
        repository.invalidateActive(normalizedEmail, purpose, now);
        EmailVerificationCode verificationCode = new EmailVerificationCode(
                idGenerator.nextId(),
                normalizedEmail,
                purpose,
                codeHash,
                VerificationCodeStatus.UNUSED,
                now,
                now.plus(timeToLive),
                null,
                0);
        repository.create(verificationCode, now);
        return verificationCode;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void invalidate(long verificationCodeId) {
        repository.invalidate(verificationCodeId, LocalDateTime.now(clock));
    }
}
