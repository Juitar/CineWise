package com.miaoyu.ticket.profile.infrastructure;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.RedisConnectionFailureException;
import org.springframework.data.redis.core.StringRedisTemplate;

class RedisProfileSummaryCacheTest {
    @Test
    void shouldNotFailWithdrawalWhenRedisIsUnavailable() {
        StringRedisTemplate redis = mock(StringRedisTemplate.class);
        when(redis.keys(anyString())).thenThrow(new RedisConnectionFailureException("redis unavailable"));
        RedisProfileSummaryCache cache = new RedisProfileSummaryCache(redis, new ObjectMapper());

        assertThatCode(() -> cache.invalidateUser(1001L)).doesNotThrowAnyException();
    }
}
