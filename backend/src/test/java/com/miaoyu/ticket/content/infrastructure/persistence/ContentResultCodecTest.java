package com.miaoyu.ticket.content.infrastructure.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
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
import org.junit.jupiter.api.Test;

class ContentResultCodecTest {

    private final ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();
    private final ContentResultCodec codec = new ContentResultCodec(objectMapper);

    @Test
    void givenJdbcWrappedJsonString_whenReadMovieSnapshot_thenRestoreNormalizedContent() throws Exception {
        ContentResult<List<? extends ContentItem>> original = new ContentResult<>(List.of(
                new MovieContent(2_001L, "netstart-movie-1", "同步验证影片", "[\"剧情\"]", 90,
                        new BigDecimal("8.0"))),
                new ContentSource("NETSTART_MAOYAN", ContentSourceType.LIVE),
                LocalDateTime.of(2026, 8, 4, 9, 0), LocalDateTime.of(2026, 8, 4, 15, 0), false, false, null);
        String jdbcWrappedPayload = objectMapper.writeValueAsString(codec.write(original));

        ContentResult<List<? extends ContentItem>> restored = codec.read(
                jdbcWrappedPayload,
                new ContentQuery(ContentResourceType.MOVIE, null, null, "同步验证影片"),
                ContentFallbackType.SNAPSHOT,
                false);

        assertThat(restored.source()).isEqualTo(original.source());
        assertThat(restored.degraded()).isTrue();
        assertThat(restored.fallbackType()).isEqualTo(ContentFallbackType.SNAPSHOT);
        assertThat(restored.data()).singleElement().isInstanceOfSatisfying(MovieContent.class, movie -> {
            assertThat(movie.movieId()).isEqualTo(2_001L);
            assertThat(movie.sourceMovieId()).isEqualTo("netstart-movie-1");
        });
    }
}
