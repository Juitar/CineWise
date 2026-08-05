package com.miaoyu.ticket.auth.application;

import com.miaoyu.ticket.auth.domain.RegistrationInvite;
import com.miaoyu.ticket.auth.domain.RegistrationInviteStatus;
import com.miaoyu.ticket.common.id.BusinessIdGenerator;
import java.time.Clock;
import java.time.DateTimeException;
import java.time.LocalDateTime;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 首个培训邀请码只补缺，不更新任何已存在的邀请码业务状态。 */
@Service
public class InitialRegistrationInviteService {

    private final RegistrationInviteRepository repository;
    private final RegistrationInviteHasher inviteHasher;
    private final BusinessIdGenerator idGenerator;
    private final Clock clock;

    public InitialRegistrationInviteService(
            RegistrationInviteRepository repository,
            RegistrationInviteHasher inviteHasher,
            BusinessIdGenerator idGenerator,
            Clock clock) {
        this.repository = repository;
        this.inviteHasher = inviteHasher;
        this.idGenerator = idGenerator;
        this.clock = clock;
    }

    @Transactional
    public boolean createIfMissing(InitialRegistrationInviteCommand command) {
        LocalDateTime validFrom = parseTime(command.validFrom(), "邀请码生效时间格式不正确");
        LocalDateTime expireTime = parseTime(command.expireTime(), "邀请码失效时间格式不正确");
        LocalDateTime now = LocalDateTime.now(clock);
        if (command.maxUses() <= 0 || !expireTime.isAfter(validFrom) || !expireTime.isAfter(now)) {
            throw new IllegalStateException("首个邀请码次数和有效期配置不合法");
        }

        String codeHash = inviteHasher.hash(command.inviteCode());
        if (repository.findByCodeHash(codeHash).isPresent()) {
            return false;
        }

        RegistrationInvite invite = new RegistrationInvite(
                idGenerator.nextId(),
                codeHash,
                RegistrationInviteStatus.ENABLED,
                command.maxUses(),
                0,
                validFrom,
                expireTime,
                0);
        try {
            return repository.createIfAbsent(invite, now);
        } catch (DuplicateKeyException exception) {
            // 多实例同时初始化时摘要唯一键只允许一个成功，已存在即按幂等成功处理。
            if (repository.findByCodeHash(codeHash).isPresent()) {
                return false;
            }
            throw exception;
        }
    }

    private LocalDateTime parseTime(String value, String message) {
        try {
            return LocalDateTime.parse(value);
        } catch (DateTimeException | NullPointerException exception) {
            throw new IllegalStateException(message, exception);
        }
    }
}
