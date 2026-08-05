package com.miaoyu.ticket.auth.domain;

import java.time.LocalDateTime;

/** 认证应用使用的验证码快照，不包含验证码明文。 */
public record EmailVerificationCode(
        long id,
        String email,
        VerificationPurpose purpose,
        String codeHash,
        VerificationCodeStatus status,
        LocalDateTime sendTime,
        LocalDateTime expireTime,
        LocalDateTime usedTime,
        int attemptCount) {
}
