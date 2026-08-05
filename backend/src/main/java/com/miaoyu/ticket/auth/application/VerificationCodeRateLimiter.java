package com.miaoyu.ticket.auth.application;

import com.miaoyu.ticket.auth.domain.VerificationPurpose;
import java.time.Duration;

/** Redis 限流端口只接收摘要，禁止把完整邮箱或 IP 放入缓存键。 */
public interface VerificationCodeRateLimiter {

    SendPermit acquire(
            String emailHash,
            String ipHash,
            VerificationPurpose purpose,
            Duration cooldown,
            Duration ipWindow,
            int maximumIpRequests);

    void releaseEmailCooldown(String emailHash, VerificationPurpose purpose);

    record SendPermit(boolean emailAllowed, boolean ipAllowed, long remainingSeconds) {
    }
}
