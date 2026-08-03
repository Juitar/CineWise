package com.miaoyu.ticket.content.infrastructure.provider;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.miaoyu.ticket.common.error.BusinessException;
import com.miaoyu.ticket.content.application.ContentQuery;
import com.miaoyu.ticket.content.application.ContentQueryService;
import com.miaoyu.ticket.content.application.ContentResult;
import com.miaoyu.ticket.content.application.ContentSeedApplicationService;
import com.miaoyu.ticket.content.application.ContentSeedCatalog;
import com.miaoyu.ticket.content.domain.ContentItem;
import com.miaoyu.ticket.content.domain.ContentResourceType;
import com.miaoyu.ticket.content.domain.MovieContent;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

/** 验证 Demo 回退通过真实内容表映射实际数据库 ID，而不是把精确查询放大为目录查询。 */
@ActiveProfiles("test")
@SpringBootTest
class DemoContentProviderFallbackIntegrationTest {

    private static final long UNKNOWN_MOVIE_ID = 8_299_999L;

    @Autowired
    private ContentQueryService contentQueryService;

    @Autowired
    private ContentSeedApplicationService contentSeedApplicationService;

    @Test
    void givenCacheAndSnapshotMiss_whenQueryingKnownDatabaseMovieId_thenDemoReturnsOnlyMappedMovie() {
        ContentSeedCatalog.MovieRef seededMovie = contentSeedApplicationService.ensureFixedSeed().movies().getFirst();

        ContentResult<List<? extends ContentItem>> result = contentQueryService.query(
                new ContentQuery(ContentResourceType.MOVIE, seededMovie.id(), null, null));

        // 缓存和快照均无记录时，精确 ID 回退仍只允许返回同一条 Demo 内容。
        assertThat(result.data()).hasSize(1);
        MovieContent movie = (MovieContent) result.data().getFirst();
        assertThat(movie.movieId()).isEqualTo(seededMovie.id());
        assertThat(movie.sourceMovieId()).isEqualTo(seededMovie.sourceMovieId());
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
