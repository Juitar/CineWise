package com.miaoyu.ticket.auth.domain;

import java.time.LocalDateTime;

/** 邀请码领域快照不包含明文，只保留并发扣减所需状态。 */
public record RegistrationInvite(
        long id,
        String codeHash,
        RegistrationInviteStatus status,
        int maxUses,
        int usedCount,
        LocalDateTime validFrom,
        LocalDateTime expireTime,
        long version) {
}
