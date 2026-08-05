package com.miaoyu.ticket.auth.application;

import com.miaoyu.ticket.auth.domain.RegistrationInvite;
import java.time.LocalDateTime;
import java.util.Optional;

/** 邀请码查询和扣减由数据库摘要索引、有效期与乐观锁共同裁决。 */
public interface RegistrationInviteRepository {

    Optional<RegistrationInvite> findByCodeHash(String codeHash);

    /** 摘要不存在时插入；已存在时返回 false，绝不覆盖次数、状态或有效期。 */
    boolean createIfAbsent(RegistrationInvite invite, LocalDateTime createTime);

    boolean consume(long inviteId, long expectedVersion, LocalDateTime now);
}
