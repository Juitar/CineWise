package com.miaoyu.ticket.agent.application.tool;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import com.miaoyu.ticket.content.application.ContentQueryService;
import com.miaoyu.ticket.content.application.ContentResult;
import com.miaoyu.ticket.content.domain.ContentItem;
import com.miaoyu.ticket.content.domain.ContentSource;
import com.miaoyu.ticket.content.domain.ContentSourceType;
import com.miaoyu.ticket.content.domain.MovieContent;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

class MovieTitleResolutionToolTest {

    @Test
    void resolvesUniqueMovieWhenUserOmitsMinorTitleParticle() {
        ContentQueryService contents = Mockito.mock(ContentQueryService.class);
        when(contents.queryLocalMovies(null, null)).thenReturn(catalog(
                movie(101L, "欢迎来到龙餐馆")));

        MovieTitleResolutionTool tool = new MovieTitleResolutionTool(contents);

        assertThat(tool.resolve("我想看欢迎来龙餐馆"))
                .hasValueSatisfying(resolved -> {
                    assertThat(resolved.movieId()).isEqualTo("101");
                    assertThat(resolved.title()).isEqualTo("欢迎来到龙餐馆");
                });
    }

    @Test
    void resolvesUniqueMovieWhenUserOnlyMentionsDistinctTitleSuffix() {
        ContentQueryService contents = Mockito.mock(ContentQueryService.class);
        when(contents.queryLocalMovies(null, null)).thenReturn(catalog(
                movie(101L, "欢迎来到龙餐馆"),
                movie(102L, "汪汪队立大功大电影3：勇闯恐龙岛")));

        MovieTitleResolutionTool tool = new MovieTitleResolutionTool(contents);

        assertThat(tool.resolve("我想看龙餐馆"))
                .hasValueSatisfying(resolved -> assertThat(resolved.movieId()).isEqualTo("101"));
    }

    @Test
    void doesNotResolveWhenTolerantMatchHasMoreThanOneCatalogCandidate() {
        ContentQueryService contents = Mockito.mock(ContentQueryService.class);
        when(contents.queryLocalMovies(null, null)).thenReturn(catalog(
                movie(101L, "欢迎来到龙餐馆"),
                movie(102L, "欢迎来龙餐馆")));

        MovieTitleResolutionTool tool = new MovieTitleResolutionTool(contents);

        assertThat(tool.resolve("我想看欢迎来龙餐馆")).isEmpty();
    }

    @Test
    void doesNotTreatOrdinaryCharacterTyposAsTitleMatches() {
        ContentQueryService contents = Mockito.mock(ContentQueryService.class);
        when(contents.queryLocalMovies(null, null)).thenReturn(catalog(
                movie(101L, "欢迎来到龙餐馆")));

        MovieTitleResolutionTool tool = new MovieTitleResolutionTool(contents);

        assertThat(tool.resolve("我想看欢迎来凤餐馆")).isEmpty();
    }

    private static Optional<ContentResult<List<? extends ContentItem>>> catalog(MovieContent... movies) {
        List<? extends ContentItem> data = List.of(movies);
        LocalDateTime dataTime = LocalDateTime.of(2026, 8, 8, 9, 0);
        return Optional.of(new ContentResult<>(data,
                new ContentSource("TEST_CATALOG", ContentSourceType.LIVE),
                dataTime, dataTime.plusHours(1), false, false, null));
    }

    private static MovieContent movie(long movieId, String title) {
        return new MovieContent(movieId, "movie-" + movieId, title, "[\"剧情\"]", 100,
                new BigDecimal("8.0"));
    }
}
