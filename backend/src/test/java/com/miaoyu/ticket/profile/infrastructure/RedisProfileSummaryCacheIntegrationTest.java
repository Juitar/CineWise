package com.miaoyu.ticket.profile.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;

import com.miaoyu.ticket.profile.application.ProfileSummary;
import com.miaoyu.ticket.profile.domain.ProfileTagPolarity;
import com.miaoyu.ticket.profile.domain.ProfileTagSource;
import com.miaoyu.ticket.profile.domain.ProfileTagType;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.test.context.ActiveProfiles;

/**
 * 使用 D 的 Redis 隧道验证摘要读写和按用户失效。
 * 只有显式启用真实 Redis 时才运行，防止日常构建意外连接共享缓存。
 */
@ActiveProfiles("test")
@SpringBootTest(properties = {
    "spring.data.redis.host=${REDIS_HOST:127.0.0.1}",
    "spring.data.redis.port=${REDIS_PORT:16379}",
    "spring.data.redis.password=${REDIS_PASSWORD:}"
})
@EnabledIfEnvironmentVariable(named = "REDIS_INTEGRATION_ENABLED", matches = "true")
class RedisProfileSummaryCacheIntegrationTest {
    private static final long TEST_USER_ID = 9_807_000_001L;
    private static final long TEST_VERSION = 17L;

    @Autowired
    private RedisProfileSummaryCache cache;

    @Autowired
    private StringRedisTemplate redisTemplate;

    @AfterEach
    void cleanCache() {
        cache.invalidateUser(TEST_USER_ID);
    }

    @Test
    void shouldRoundTripSummaryAndInvalidateAllVersionsForUser() {
        Instant generatedAt = Instant.parse("2026-08-07T01:00:00Z");
        ProfileSummary summary = new ProfileSummary(true, TEST_VERSION, generatedAt, List.of(
                new ProfileSummary.Tag(ProfileTagType.MOVIE_GENRE, "科幻", ProfileTagPolarity.LIKE,
                        new BigDecimal("0.800"), new BigDecimal("0.900"), ProfileTagSource.CONVERSATION,
                        generatedAt)));

        cache.put(TEST_USER_ID, TEST_VERSION, summary);

        assertThat(cache.find(TEST_USER_ID, TEST_VERSION)).contains(summary);
        assertThat(redisTemplate.hasKey(ProfileCacheKeyFactory.summaryKey(TEST_USER_ID, TEST_VERSION))).isTrue();

        cache.invalidateUser(TEST_USER_ID);

        assertThat(cache.find(TEST_USER_ID, TEST_VERSION)).isEmpty();
        assertThat(redisTemplate.hasKey(ProfileCacheKeyFactory.summaryKey(TEST_USER_ID, TEST_VERSION))).isFalse();
    }
}
