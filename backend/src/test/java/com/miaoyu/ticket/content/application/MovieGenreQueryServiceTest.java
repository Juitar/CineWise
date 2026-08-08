package com.miaoyu.ticket.content.application;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.miaoyu.ticket.content.domain.ContentFallbackType;
import com.miaoyu.ticket.content.domain.ContentItem;
import com.miaoyu.ticket.content.domain.ContentResourceType;
import com.miaoyu.ticket.content.domain.ContentSource;
import com.miaoyu.ticket.content.domain.ContentSourceType;
import com.miaoyu.ticket.content.domain.MovieContent;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class MovieGenreQueryServiceTest {
    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-08-08T09:00:00Z"), ZoneOffset.UTC);
    private static final LocalDateTime NOW = LocalDateTime.ofInstant(CLOCK.instant(), ZoneOffset.UTC);

    @Test
    void shouldReturnFirstNonBlankGenreForTheRequestedMovie() {
        MovieGenreQueryService service = service(Optional.of(movieResult(31L, "[\"\", \"科幻\", \"冒险\"]")));

        assertThat(service.findPrimaryGenre("31")).contains("科幻");
    }

    @Test
    void shouldReturnEmptyForInvalidIdMissingMovieOrInvalidGenreJson() {
        MovieGenreQueryService missingMovie = service(Optional.of(movieResult(32L, "[\"科幻\"]")));
        MovieGenreQueryService invalidGenres = service(Optional.of(movieResult(31L, "not-json")));

        assertThat(missingMovie.findPrimaryGenre("31")).isEmpty();
        assertThat(invalidGenres.findPrimaryGenre("31")).isEmpty();
        assertThat(missingMovie.findPrimaryGenre("not-a-number")).isEmpty();
        assertThat(missingMovie.findPrimaryGenre(null)).isEmpty();
    }

    @Test
    void shouldReturnEmptyWhenContentIsUnavailable() {
        MovieGenreQueryService service = service(Optional.empty());

        assertThat(service.findPrimaryGenre("31")).isEmpty();
    }

    @Test
    void shouldReturnEmptyWhenContentQueryThrowsRuntimeException() {
        ContentQueryService content = new ContentQueryService(emptyCache(), emptySnapshot(), query -> {
            throw new IllegalStateException("content provider unavailable");
        }, new ContentProperties(Duration.ofHours(6), Duration.ofHours(6), Duration.ofDays(7)), CLOCK);
        MovieGenreQueryService service = new MovieGenreQueryService(content, new ObjectMapper());

        assertThat(service.findPrimaryGenre("31")).isEmpty();
        assertThat(service.findPrimaryGenre("not-a-number")).isEmpty();
    }

    private MovieGenreQueryService service(Optional<ContentResult<List<? extends ContentItem>>> demoResult) {
        ContentQueryService content = new ContentQueryService(emptyCache(), emptySnapshot(), query -> demoResult,
                new ContentProperties(Duration.ofHours(6), Duration.ofHours(6), Duration.ofDays(7)), CLOCK);
        return new MovieGenreQueryService(content, new ObjectMapper());
    }

    private ContentCachePort emptyCache() {
        return new ContentCachePort() {
            @Override
            public Optional<ContentResult<List<? extends ContentItem>>> find(ContentQuery query) {
                return Optional.empty();
            }

            @Override
            public void save(ContentQuery query, ContentResult<List<? extends ContentItem>> result) { }
        };
    }

    private ContentSnapshotPort emptySnapshot() {
        return new ContentSnapshotPort() {
            @Override
            public Optional<ContentResult<List<? extends ContentItem>>> findLatest(ContentQuery query) {
                return Optional.empty();
            }

            @Override
            public void save(ContentQuery query, ContentResult<List<? extends ContentItem>> result) { }
        };
    }

    private ContentResult<List<? extends ContentItem>> movieResult(long movieId, String genresJson) {
        MovieContent movie = new MovieContent(movieId, "source-" + movieId, "测试影片", genresJson, 100,
                new BigDecimal("8.0"));
        return new ContentResult<>(List.of(movie), new ContentSource("DEMO_CONTENT", ContentSourceType.MOCK), NOW,
                NOW.plusHours(1), false, true, ContentFallbackType.MOCK);
    }
}
