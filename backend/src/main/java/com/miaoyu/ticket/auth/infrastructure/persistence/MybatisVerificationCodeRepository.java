package com.miaoyu.ticket.auth.infrastructure.persistence;

import com.miaoyu.ticket.auth.application.VerificationCodeRepository;
import com.miaoyu.ticket.auth.domain.EmailVerificationCode;
import com.miaoyu.ticket.auth.domain.VerificationCodeStatus;
import com.miaoyu.ticket.auth.domain.VerificationPurpose;
import java.time.LocalDateTime;
import java.util.Optional;
import org.springframework.stereotype.Repository;

/** 将验证码数据库行转换为不依赖 MyBatis 的领域快照。 */
@Repository
public class MybatisVerificationCodeRepository implements VerificationCodeRepository {

    private final VerificationCodeMapper mapper;

    public MybatisVerificationCodeRepository(VerificationCodeMapper mapper) {
        this.mapper = mapper;
    }

    @Override
    public void invalidateActive(
            String normalizedEmail, VerificationPurpose purpose, LocalDateTime updateTime) {
        mapper.invalidateActive(normalizedEmail, purpose, updateTime);
    }

    @Override
    public void create(EmailVerificationCode code, LocalDateTime createTime) {
        mapper.insert(code, createTime);
    }

    @Override
    public Optional<EmailVerificationCode> findLatestUsable(
            String normalizedEmail,
            VerificationPurpose purpose,
            LocalDateTime now,
            int maximumAttempts) {
        return Optional.ofNullable(mapper.findLatestUsable(normalizedEmail, purpose, now, maximumAttempts))
                .map(this::toDomain);
    }

    @Override
    public boolean consume(long id, int expectedAttemptCount, LocalDateTime usedTime) {
        return mapper.consume(id, expectedAttemptCount, usedTime) == 1;
    }

    @Override
    public boolean recordFailedAttempt(
            long id, int expectedAttemptCount, int maximumAttempts, LocalDateTime updateTime) {
        return mapper.recordFailedAttempt(id, expectedAttemptCount, maximumAttempts, updateTime) == 1;
    }

    @Override
    public boolean invalidate(long id, LocalDateTime updateTime) {
        return mapper.invalidate(id, updateTime) == 1;
    }

    private EmailVerificationCode toDomain(VerificationCodeRow row) {
        return new EmailVerificationCode(
                row.id(),
                row.email(),
                VerificationPurpose.valueOf(row.purpose()),
                row.codeHash(),
                VerificationCodeStatus.valueOf(row.status()),
                row.sendTime(),
                row.expireTime(),
                row.usedTime(),
                row.attemptCount());
    }
}
