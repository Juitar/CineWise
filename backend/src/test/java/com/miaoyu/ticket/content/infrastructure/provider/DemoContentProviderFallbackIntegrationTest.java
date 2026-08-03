package com.miaoyu.ticket.content.infrastructure.provider;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.miaoyu.ticket.common.error.BusinessException;
import com.miaoyu.ticket.content.application.ContentQuery;
import com.miaoyu.ticket.content.application.ContentQueryService;
import com.miaoyu.ticket.content.application.ContentResult;
import com.miaoyu.ticket.content.domain.ContentItem;
import com.miaoyu.ticket.content.domain.ContentResourceType;
import com.miaoyu.ticket.content.domain.MovieContent;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

/** 验证 Demo 回退通过真实内容表映射实际数据库 ID，而不是把精确查询放大为目录查询。 */
@ActiveProfiles("test")
@SpringBootTest
class DemoContentProviderFallbackIntegrationTest {

    private static final long KNOWN_MOVIE_ID = 8_200_001L;
    private static final long UNKNOWN_MOVIE_ID = 8_299_999L;

    @Autowired
    private ContentQueryService contentQueryService;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @AfterEach
    void removeMovies() {
        jdbcTemplate.update("DELETE FROM movie WHERE id IN (?, ?)", KNOWN_MOVIE_ID, UNKNOWN_MOVIE_ID);
    }

    @Test
    void givenCacheAndSnapshotMiss_whenQueryingKnownDatabaseMovieId_thenDemoReturnsOnlyMappedMovie() {
        LocalDateTime dataTime = LocalDateTime.of(2026, 8, 3, 9, 0);
        jdbcTemplate.update("""
                INSERT INTO movie (id, source_movie_id, title, genres_json, duration_minutes, rating, source_type,
                source, data_time, expires_at, version, create_time, update_time)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, 0, ?, ?)
                """, KNOWN_MOVIE_ID, "mock-movie-01", "星河远征", "[\"科幻\",\"冒险\"]", 128,
                new java.math.BigDecimal("8.6"), "MOCK", "DEMO_CONTENT", Timestamp.valueOf(dataTime),
                Timestamp.valueOf(dataTime.plusHours(6)), Timestamp.valueOf(dataTime), Timestamp.valueOf(dataTime));

        ContentResult<List<? extends ContentItem>> result = contentQueryService.query(
                new ContentQuery(ContentResourceType.MOVIE, KNOWN_MOVIE_ID, null, null));

        // 缓存和快照均无记录时，精确 ID 回退仍只允许返回同一条 Demo 内容。
        assertThat(result.data()).hasSize(1);
        MovieContent movie = (MovieContent) result.data().getFirst();
        assertThat(movie.movieId()).isEqualTo(KNOWN_MOVIE_ID);
        assertThat(movie.sourceMovieId()).isEqualTo("mock-movie-01");
    }

    @Test
    void givenCacheAndSnapshotMiss_whenQueryingUnknownDatabaseMovieId_thenDemoDoesNotReturnCatalogList() {
        assertThatThrownBy(() -> contentQueryService.query(
                new ContentQuery(ContentResourceType.MOVIE, UNKNOWN_MOVIE_ID, null, null)))
                .isInstanceOf(BusinessException.class)
                .extracting(exception -> ((BusinessException) exception).getErrorCode().code())
                .isEqualTo(303004);
    }
}
