package com.miaoyu.ticket.profile.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;

import com.miaoyu.ticket.profile.application.ProfileSummary;
import com.miaoyu.ticket.profile.application.ProfileSummaryCache;
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
 * 使用 D 的 Redis 隧道或 CI 一次性 Redis 验证画像摘要缓存。
 * 只有显式启用真实 Redis 时才运行，避免日常构建意外连接共享缓存；清理只影响本测试的专属键。
 */
@ActiveProfiles("test")
@SpringBootTest(properties = {
    "spring.data.redis.host=${SPRING_DATA_REDIS_HOST:127.0.0.1}",
    "spring.data.redis.port=${SPRING_DATA_REDIS_PORT:16379}",
    "spring.data.redis.password=${SPRING_DATA_REDIS_PASSWORD:}"
})
@EnabledIfEnvironmentVariable(named = "REDIS_INTEGRATION_ENABLED", matches = "true")
class RedisProfileSummaryCacheIntegrationTest {
    private static final long TEST_USER_ID = 9_807_000_001L;
    private static final long OTHER_TEST_USER_ID = 9_807_000_002L;
    private static final long TEST_VERSION = 17L;
    private static final List<String> TEST_KEYS = List.of(
            ProfileCacheKeyFactory.summaryKey(TEST_USER_ID, TEST_VERSION),
            ProfileCacheKeyFactory.summaryKey(TEST_USER_ID, TEST_VERSION + 1),
            ProfileCacheKeyFactory.summaryKey(OTHER_TEST_USER_ID, TEST_VERSION));

    @Autowired
    private ProfileSummaryCache summaryCache;

    @Autowired
    private StringRedisTemplate redisTemplate;

    @AfterEach
    void cleanCache() {
        redisTemplate.delete(TEST_KEYS);
    }

    @Test
    void shouldRoundTripSummaryAndInvalidateAllVersionsForUser() {
        Instant generatedAt = Instant.parse("2026-08-07T01:00:00Z");
        ProfileSummary summary = new ProfileSummary(true, TEST_VERSION, generatedAt, List.of(
                new ProfileSummary.Tag(ProfileTagType.MOVIE_GENRE, "科幻", ProfileTagPolarity.LIKE,
                        new BigDecimal("0.800"), new BigDecimal("0.900"), ProfileTagSource.CONVERSATION,
                        generatedAt)));

        summaryCache.put(TEST_USER_ID, TEST_VERSION, summary);

        assertThat(summaryCache.find(TEST_USER_ID, TEST_VERSION)).contains(summary);
        assertThat(redisTemplate.hasKey(TEST_KEYS.getFirst())).isTrue();

        summaryCache.invalidateUser(TEST_USER_ID);

        assertThat(summaryCache.find(TEST_USER_ID, TEST_VERSION)).isEmpty();
        assertThat(redisTemplate.hasKey(TEST_KEYS.getFirst())).isFalse();
    }

    @Test
    void shouldDeleteAllTargetUserSummariesAndKeepOtherUsers() {
        TEST_KEYS.forEach(key -> redisTemplate.opsForValue().set(key, "{}"));

        summaryCache.invalidateUser(TEST_USER_ID);

        assertThat(redisTemplate.hasKey(TEST_KEYS.getFirst())).isFalse();
        assertThat(redisTemplate.hasKey(TEST_KEYS.get(1))).isFalse();
        assertThat(redisTemplate.hasKey(TEST_KEYS.get(2))).isTrue();
    }
}
