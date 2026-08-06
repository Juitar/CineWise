package com.miaoyu.ticket.auth.infrastructure.persistence;

import com.miaoyu.ticket.auth.application.AuthUserRepository;
import com.miaoyu.ticket.auth.application.RoleCode;
import com.miaoyu.ticket.auth.domain.AccountStatus;
import com.miaoyu.ticket.auth.domain.AuthUser;
import java.time.LocalDateTime;
import java.util.Optional;
import org.springframework.stereotype.Repository;

/** 将持久化行转换为无框架依赖的认证领域快照。 */
@Repository
public class MybatisAuthUserRepository implements AuthUserRepository {

    private final AuthUserMapper mapper;

    public MybatisAuthUserRepository(AuthUserMapper mapper) {
        this.mapper = mapper;
    }

    @Override
    public Optional<AuthUser> findByEmail(String normalizedEmail) {
        return Optional.ofNullable(mapper.findByEmail(normalizedEmail)).map(this::toDomain);
    }

    @Override
    public Optional<AuthUser> findById(long userId) {
        return Optional.ofNullable(mapper.findById(userId)).map(this::toDomain);
    }

    @Override
    public boolean incrementTokenVersion(long userId, long expectedTokenVersion, LocalDateTime updateTime) {
        return mapper.incrementTokenVersion(userId, expectedTokenVersion, updateTime) == 1;
    }

    @Override
    public boolean resetPassword(
            long userId, long expectedTokenVersion, String passwordHash, LocalDateTime updateTime) {
        return mapper.resetPassword(userId, expectedTokenVersion, passwordHash, updateTime) == 1;
    }

    @Override
    public boolean existsByEmail(String normalizedEmail) {
        return mapper.countByEmail(normalizedEmail) > 0;
    }

    @Override
    public void create(AuthUser user, LocalDateTime createTime) {
        mapper.insert(user, createTime);
    }

    private AuthUser toDomain(AuthUserRow row) {
        return new AuthUser(
                row.id(),
                row.email(),
                row.passwordHash(),
                row.nickname(),
                RoleCode.valueOf(row.roleCode()),
                AccountStatus.valueOf(row.status()),
                row.emailVerified(),
                row.tokenVersion(),
                row.privacyPolicyVersion(),
                row.privacyAcceptedAt());
    }
}
