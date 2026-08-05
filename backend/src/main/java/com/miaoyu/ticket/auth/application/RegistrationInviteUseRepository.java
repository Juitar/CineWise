package com.miaoyu.ticket.auth.application;

import com.miaoyu.ticket.auth.domain.RegistrationInviteUse;
import java.time.LocalDateTime;
import java.util.Optional;

/** 使用记录通过唯一 clientRequestId 支持安全恢复，通过唯一 userId 防重复登记。 */
public interface RegistrationInviteUseRepository {

    Optional<RegistrationInviteUse> findByClientRequestId(String clientRequestId);

    void create(RegistrationInviteUse use, LocalDateTime createTime);
}
