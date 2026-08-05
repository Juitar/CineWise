package com.miaoyu.ticket.auth.infrastructure.rate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.miaoyu.ticket.auth.application.VerificationCodeRateLimiter;
import com.miaoyu.ticket.auth.domain.VerificationPurpose;
import java.time.Duration;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

class RedisVerificationCodeRateLimiterTest {

    private final StringRedisTemplate redisTemplate = mock(StringRedisTemplate.class);
    private final ValueOperations<String, String> valueOperations = mock(ValueOperations.class);
    private RedisVerificationCodeRateLimiter rateLimiter;

    @BeforeEach
    void setUp() {
        rateLimiter = new RedisVerificationCodeRateLimiter(redisTemplate);
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
    }

    @Test
    void shouldAtomicallyAcquireIpWindowAndEmailCooldown() {
        when(redisTemplate.execute(any(), anyList(), any())).thenReturn(1L);
        when(valueOperations.setIfAbsent(
                        "auth:verification:email:LOGIN:email-hash", "1", Duration.ofSeconds(60)))
                .thenReturn(true);

        VerificationCodeRateLimiter.SendPermit permit = rateLimiter.acquire(
                "email-hash",
                "ip-hash",
                VerificationPurpose.LOGIN,
                Duration.ofSeconds(60),
                Duration.ofMinutes(5),
                10);

        assertThat(permit).isEqualTo(new VerificationCodeRateLimiter.SendPermit(true, true, 60));
    }

    @Test
    void shouldReturnExistingEmailCooldownWithoutAcquiringAgain() {
        when(redisTemplate.execute(any(), anyList(), any())).thenReturn(2L);
        when(valueOperations.setIfAbsent(
                        "auth:verification:email:LOGIN:email-hash", "1", Duration.ofSeconds(60)))
                .thenReturn(false);
        when(redisTemplate.getExpire(
                        "auth:verification:email:LOGIN:email-hash", TimeUnit.SECONDS))
                .thenReturn(37L);

        VerificationCodeRateLimiter.SendPermit permit = rateLimiter.acquire(
                "email-hash",
                "ip-hash",
                VerificationPurpose.LOGIN,
                Duration.ofSeconds(60),
                Duration.ofMinutes(5),
                10);

        assertThat(permit).isEqualTo(new VerificationCodeRateLimiter.SendPermit(false, true, 37));
    }

    @Test
    void shouldRejectIpBeforeCreatingEmailCooldown() {
        when(redisTemplate.execute(any(), anyList(), any())).thenReturn(11L);
        when(redisTemplate.getExpire(eq("auth:verification:ip:ip-hash"), eq(TimeUnit.SECONDS)))
                .thenReturn(120L);

        VerificationCodeRateLimiter.SendPermit permit = rateLimiter.acquire(
                "email-hash",
                "ip-hash",
                VerificationPurpose.LOGIN,
                Duration.ofSeconds(60),
                Duration.ofMinutes(5),
                10);

        assertThat(permit).isEqualTo(new VerificationCodeRateLimiter.SendPermit(false, false, 120));
        verify(valueOperations, org.mockito.Mockito.never()).setIfAbsent(any(), any(), any(Duration.class));
    }

    @Test
    void shouldReleaseOnlyEmailPurposeCooldown() {
        rateLimiter.releaseEmailCooldown("email-hash", VerificationPurpose.REGISTER);

        verify(redisTemplate).delete("auth:verification:email:REGISTER:email-hash");
    }
}
