package com.miaoyu.ticket.content.infrastructure.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import com.miaoyu.ticket.content.application.ContentCachePort;
import com.miaoyu.ticket.content.application.ContentQuery;
import com.miaoyu.ticket.content.application.ContentResult;
import com.miaoyu.ticket.content.domain.ContentFallbackType;
import com.miaoyu.ticket.content.domain.ContentItem;
import com.miaoyu.ticket.content.domain.ContentResourceType;
import com.miaoyu.ticket.content.domain.ContentSource;
import com.miaoyu.ticket.content.domain.ContentSourceType;
import com.miaoyu.ticket.content.domain.MovieContent;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.test.context.ActiveProfiles;

@ActiveProfiles("test")
@SpringBootTest
class RedisContentCacheAdapterIntegrationTest {

    private static final ContentQuery QUERY = new ContentQuery(ContentResourceType.MOVIE, null, "330100", "缓存验证");

    @Autowired
    private ContentCachePort contentCachePort;

    @Autowired
    private StringRedisTemplate redisTemplate;

    @AfterEach
    void clearCacheEntry() {
        redisTemplate.delete(ContentCacheKeyFactory.create(QUERY));
    }

    @Test
    void givenRunningRedis_whenSavingNormalizedContent_thenItCanBeReadBackAsCacheFallback() {
        LocalDateTime dataTime = LocalDateTime.of(2027, 8, 3, 9, 0);
        ContentResult<List<? extends ContentItem>> content = new ContentResult<>(List.of(new MovieContent(
                "cache-movie", "缓存验证影片", "[\"剧情\"]", 100, new BigDecimal("8.0"))),
                new ContentSource("TEST", ContentSourceType.MOCK), dataTime, dataTime.plusHours(6), false,
                true, ContentFallbackType.MOCK);

        contentCachePort.save(QUERY, content);

        // 真 Redis 读回后必须仍是标准内容，并明确说明数据来自缓存回退层。
        ContentResult<List<? extends ContentItem>> cached = contentCachePort.find(QUERY).orElseThrow();
        assertThat(cached.fallbackType()).isEqualTo(ContentFallbackType.CACHE);
        assertThat(cached.data()).hasSize(1);
        assertThat(cached.source().name()).isEqualTo("TEST");
    }
}
