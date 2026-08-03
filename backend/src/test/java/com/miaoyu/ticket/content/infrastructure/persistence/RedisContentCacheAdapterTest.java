package com.miaoyu.ticket.content.infrastructure.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.miaoyu.ticket.content.application.ContentProperties;
import com.miaoyu.ticket.content.application.ContentQuery;
import com.miaoyu.ticket.content.domain.ContentResourceType;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.RedisConnectionFailureException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

class RedisContentCacheAdapterTest {

    @Test
    void givenRedisReadFailure_whenFindingCache_thenItReturnsMissForSnapshotFallback() {
        StringRedisTemplate redisTemplate = mock(StringRedisTemplate.class);
        @SuppressWarnings("unchecked")
        ValueOperations<String, String> values = mock(ValueOperations.class);
        when(redisTemplate.opsForValue()).thenReturn(values);
        when(values.get(anyString())).thenThrow(new RedisConnectionFailureException("Redis 不可用"));
        RedisContentCacheAdapter adapter = new RedisContentCacheAdapter(redisTemplate, new ObjectMapper(),
                new ContentProperties(Duration.ofHours(6), Duration.ofHours(6), Duration.ofDays(7)),
                Clock.fixed(Instant.parse("2026-08-03T01:00:00Z"), ZoneId.of("Asia/Shanghai")));

        // Redis 读取失败只能视为缓存未命中，主查询仍要有机会读取快照或 Demo。
        assertThat(adapter.find(new ContentQuery(ContentResourceType.MOVIE, null, "330100", "星河"))).isEmpty();
    }
}
