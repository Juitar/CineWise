package com.miaoyu.ticket.auth.infrastructure.rate;

import com.miaoyu.ticket.auth.application.VerificationCodeRateLimiter;
import com.miaoyu.ticket.auth.domain.VerificationPurpose;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.TimeUnit;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Component;

/** Redis 只保存不可逆摘要键；IP 固定窗口的计数与首次 TTL 通过 Lua 原子完成。 */
@Component
public class RedisVerificationCodeRateLimiter implements VerificationCodeRateLimiter {

    private static final String PREFIX = "auth:verification:";
    private static final DefaultRedisScript<Long> IP_LIMIT_SCRIPT = new DefaultRedisScript<>("""
            local count = redis.call('INCR', KEYS[1])
            if count == 1 then
              redis.call('PEXPIRE', KEYS[1], ARGV[1])
            end
            return count
            """, Long.class);

    private final StringRedisTemplate redisTemplate;

    public RedisVerificationCodeRateLimiter(StringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    @Override
    public SendPermit acquire(
            String emailHash,
            String ipHash,
            VerificationPurpose purpose,
            Duration cooldown,
            Duration ipWindow,
            int maximumIpRequests) {
        Long ipCount = redisTemplate.execute(
                IP_LIMIT_SCRIPT,
                List.of(ipKey(ipHash)),
                Long.toString(ipWindow.toMillis()));
        if (ipCount == null) {
            throw new IllegalStateException("Redis 未返回验证码 IP 限流结果");
        }
        if (ipCount > maximumIpRequests) {
            return new SendPermit(false, false, remainingSeconds(ipKey(ipHash)));
        }

        String emailKey = emailKey(emailHash, purpose);
        Boolean acquired = redisTemplate.opsForValue().setIfAbsent(emailKey, "1", cooldown);
        if (acquired == null) {
            throw new IllegalStateException("Redis 未返回验证码冷却结果");
        }
        if (!acquired) {
            return new SendPermit(false, true, remainingSeconds(emailKey));
        }
        return new SendPermit(true, true, cooldown.toSeconds());
    }

    @Override
    public void releaseEmailCooldown(String emailHash, VerificationPurpose purpose) {
        redisTemplate.delete(emailKey(emailHash, purpose));
    }

    private long remainingSeconds(String key) {
        Long ttl = redisTemplate.getExpire(key, TimeUnit.SECONDS);
        return ttl == null || ttl < 1 ? 1 : ttl;
    }

    private String emailKey(String emailHash, VerificationPurpose purpose) {
        return PREFIX + "email:" + purpose.name() + ":" + emailHash;
    }

    private String ipKey(String ipHash) {
        return PREFIX + "ip:" + ipHash;
    }
}
