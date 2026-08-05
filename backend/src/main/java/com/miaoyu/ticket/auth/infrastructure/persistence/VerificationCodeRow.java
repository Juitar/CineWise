package com.miaoyu.ticket.auth.infrastructure.persistence;

import java.time.LocalDateTime;

/** 验证码持久化行不包含明文验证码。 */
public record VerificationCodeRow(
        long id,
        String email,
        String purpose,
        String codeHash,
        String status,
        LocalDateTime sendTime,
        LocalDateTime expireTime,
        LocalDateTime usedTime,
        int attemptCount) {
}
