package com.miaoyu.ticket.auth.infrastructure.persistence;

import java.time.LocalDateTime;

/** 邀请码持久化行不包含明文。 */
public record RegistrationInviteRow(
        long id,
        String codeHash,
        String status,
        int maxUses,
        int usedCount,
        LocalDateTime validFrom,
        LocalDateTime expireTime,
        long version) {
}
