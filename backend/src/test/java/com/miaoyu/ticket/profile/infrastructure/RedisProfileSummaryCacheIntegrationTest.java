package com.miaoyu.ticket.profile.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;

import com.miaoyu.ticket.profile.application.ProfileSummaryCache;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.test.context.ActiveProfiles;

/** 使用 CI 一次性 Redis 验证撤回只清理目标用户的全部画像摘要键。 */
@ActiveProfiles("test")
@SpringBootTest
@EnabledIfEnvironmentVariable(named = "REDIS_INTEGRATION_ENABLED", matches = "true")
class RedisProfileSummaryCacheIntegrationTest {
    private static final long USER_ID = 9_717_000_001L;
    private static final long OTHER_USER_ID = 9_717_000_002L;
    private static final List<String> TEST_KEYS = List.of(
            ProfileCacheKeyFactory.summaryKey(USER_ID, 1L),
            ProfileCacheKeyFactory.summaryKey(USER_ID, 2L),
            ProfileCacheKeyFactory.summaryKey(OTHER_USER_ID, 1L));

    @Autowired
    private ProfileSummaryCache summaryCache;

    @Autowired
    private StringRedisTemplate redisTemplate;

    @AfterEach
    void cleanupKeys() {
        redisTemplate.delete(TEST_KEYS);
    }

    @Test
    void shouldDeleteAllTargetUserSummariesAndKeepOtherUsers() {
        TEST_KEYS.forEach(key -> redisTemplate.opsForValue().set(key, "{}"));

        summaryCache.invalidateUser(USER_ID);

        assertThat(redisTemplate.hasKey(TEST_KEYS.get(0))).isFalse();
        assertThat(redisTemplate.hasKey(TEST_KEYS.get(1))).isFalse();
        assertThat(redisTemplate.hasKey(TEST_KEYS.get(2))).isTrue();
    }
}
