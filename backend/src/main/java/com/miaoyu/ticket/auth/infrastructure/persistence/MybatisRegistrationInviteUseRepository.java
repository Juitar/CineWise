package com.miaoyu.ticket.auth.infrastructure.persistence;

import com.miaoyu.ticket.auth.application.RegistrationInviteUseRepository;
import com.miaoyu.ticket.auth.domain.RegistrationInviteUse;
import java.time.LocalDateTime;
import java.util.Optional;
import org.springframework.stereotype.Repository;

/** 使用记录映射不向应用层暴露 MyBatis 行类型。 */
@Repository
public class MybatisRegistrationInviteUseRepository implements RegistrationInviteUseRepository {

    private final RegistrationInviteUseMapper mapper;

    public MybatisRegistrationInviteUseRepository(RegistrationInviteUseMapper mapper) {
        this.mapper = mapper;
    }

    @Override
    public Optional<RegistrationInviteUse> findByClientRequestId(String clientRequestId) {
        return Optional.ofNullable(mapper.findByClientRequestId(clientRequestId)).map(this::toDomain);
    }

    @Override
    public void create(RegistrationInviteUse use, LocalDateTime createTime) {
        mapper.insert(use, createTime);
    }

    private RegistrationInviteUse toDomain(RegistrationInviteUseRow row) {
        return new RegistrationInviteUse(
                row.id(), row.inviteId(), row.userId(), row.clientRequestId(), row.usedAt());
    }
}
