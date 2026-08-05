package com.miaoyu.ticket.auth.infrastructure.persistence;

import com.miaoyu.ticket.auth.application.RegistrationInviteRepository;
import com.miaoyu.ticket.auth.domain.RegistrationInvite;
import com.miaoyu.ticket.auth.domain.RegistrationInviteStatus;
import java.time.LocalDateTime;
import java.util.Optional;
import org.springframework.stereotype.Repository;

/** 将邀请码行转换为无框架依赖的领域快照。 */
@Repository
public class MybatisRegistrationInviteRepository implements RegistrationInviteRepository {

    private final RegistrationInviteMapper mapper;

    public MybatisRegistrationInviteRepository(RegistrationInviteMapper mapper) {
        this.mapper = mapper;
    }

    @Override
    public Optional<RegistrationInvite> findByCodeHash(String codeHash) {
        return Optional.ofNullable(mapper.findByCodeHash(codeHash)).map(this::toDomain);
    }

    @Override
    public boolean createIfAbsent(RegistrationInvite invite, LocalDateTime createTime) {
        return mapper.insertIfAbsent(invite, createTime) == 1;
    }

    @Override
    public boolean consume(long inviteId, long expectedVersion, LocalDateTime now) {
        return mapper.consume(inviteId, expectedVersion, now) == 1;
    }

    private RegistrationInvite toDomain(RegistrationInviteRow row) {
        return new RegistrationInvite(
                row.id(),
                row.codeHash(),
                RegistrationInviteStatus.valueOf(row.status()),
                row.maxUses(),
                row.usedCount(),
                row.validFrom(),
                row.expireTime(),
                row.version());
    }
}
