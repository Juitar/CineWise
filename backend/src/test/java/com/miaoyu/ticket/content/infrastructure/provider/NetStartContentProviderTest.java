package com.miaoyu.ticket.content.infrastructure.provider;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.miaoyu.ticket.content.application.ContentQuery;
import com.miaoyu.ticket.content.application.ContentResult;
import com.miaoyu.ticket.content.application.ContentPersistencePort;
import com.miaoyu.ticket.content.domain.ContentItem;
import com.miaoyu.ticket.content.domain.ContentResourceType;
import com.miaoyu.ticket.content.domain.ContentSourceType;
import com.miaoyu.ticket.content.domain.MovieContent;
import com.miaoyu.ticket.content.domain.CinemaContent;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.http.HttpStatus;

class NetStartContentProviderTest {
    private static final ObjectMapper JSON = new ObjectMapper();
    private static final Clock CLOCK = Clock.fixed(
            Instant.parse("2026-08-04T08:00:00Z"), ZoneId.of("Asia/Shanghai"));

    @Test
    void givenCurrentDetailShape_whenNormalize_thenOnlyCompleteMovieFieldsBecomeLiveContent() throws Exception {
        NetStartContentProvider provider = provider(query -> json("""
                {"detailMovie":{"id":1525000,"nm":"年会不能停！2","cat":"剧情,喜剧","dur":"118分钟","sc":"9.6",
                "img":"https://p0.meituan.net/movie/test.jpg","dra":"合格短简介","rt":"2026-08-01",
                "globalReleased":true,"showInfo":"今天282家影院放映1596场","commentedUsers":123}}"""));

        ContentResult<List<? extends ContentItem>> result = provider.query(movieDetail()).orElseThrow();

        MovieContent movie = (MovieContent) result.data().getFirst();
        // 上游的场次数、影评等字段只能证明其存在，不能穿透为 A 的票务事实或 D 的内容字段。
        assertThat(movie.sourceMovieId()).isEqualTo("1525000");
        assertThat(movie.genresJson()).isEqualTo("[\"剧情\",\"喜剧\"]");
        assertThat(movie.durationMinutes()).isEqualTo(118);
        assertThat(movie.posterUrl()).isEqualTo("https://p0.meituan.net/movie/test.jpg");
        assertThat(movie.summary()).isEqualTo("合格短简介");
        assertThat(movie.releaseDate()).isEqualTo("2026-08-01");
        assertThat(movie.releaseStatus()).isEqualTo("NOW_SHOWING");
        assertThat(result.source().type()).isEqualTo(ContentSourceType.LIVE);
        assertThat(result.degraded()).isFalse();
    }

    @Test
    void givenHttpOrRelativePoster_whenNormalize_thenItIsDiscardedWithoutRejectingMovie() throws Exception {
        NetStartContentProvider provider = provider(query -> json("""
                {"detailMovie":{"id":1525002,"nm":"测试影片","cat":"剧情","dur":"90分钟","sc":"8.0",
                "img":"http://example.test/poster.jpg","dra":"  ","rt":"2026-09-01"}}"""));

        MovieContent movie = (MovieContent) provider.query(movieDetail()).orElseThrow().data().getFirst();

        // 海报是可选资料，HTTP 地址只置空，不能让一部最低字段合格的影片整体丢失。
        assertThat(movie.posterUrl()).isNull();
        assertThat(movie.summary()).isNull();
        assertThat(movie.releaseStatus()).isEqualTo("COMING_SOON");
    }

    @Test
    void givenRelativeOrBlankPoster_whenNormalize_thenItIsDiscardedWithoutRejectingMovie() throws Exception {
        NetStartContentProvider relative = provider(query -> json("""
                {"detailMovie":{"id":1525003,"nm":"相对地址片","cat":"剧情","dur":"90分钟","sc":"8.0",
                "img":"/movie/poster.jpg"}}"""));
        NetStartContentProvider blank = provider(query -> json("""
                {"detailMovie":{"id":1525004,"nm":"空海报片","cat":"剧情","dur":"90分钟","sc":"8.0",
                "img":"  "}}"""));

        assertThat(((MovieContent) relative.query(movieDetail()).orElseThrow().data().getFirst()).posterUrl()).isNull();
        assertThat(((MovieContent) blank.query(movieDetail()).orElseThrow().data().getFirst()).posterUrl()).isNull();
    }

    @Test
    void givenCurrentMovieWrapper_whenNormalize_thenItKeepsTheSameMovieFields() throws Exception {
        NetStartContentProvider provider = provider(query -> json("""
                {"movie":{"id":1525001,"nm":"测试影片","cat":"剧情,喜剧","dur":"118分钟","sc":"9.6",
                "comments":{"content":"不应保存"},"showInfo":"不应保存"}}"""));

        ContentResult<List<? extends ContentItem>> result = provider.query(movieDetail()).orElseThrow();

        MovieContent movie = (MovieContent) result.data().getFirst();
        // 当前页面详情把最低字段放入 movie；评论和场次说明仍不得进入内容模型。
        assertThat(movie.sourceMovieId()).isEqualTo("1525001");
        assertThat(movie.genresJson()).isEqualTo("[\"剧情\",\"喜剧\"]");
        assertThat(movie.durationMinutes()).isEqualTo(118);
    }

    @Test
    void givenHotListWithoutGenresAndDuration_whenQuery_thenItIsRejectedInsteadOfPretendingToBeComplete()
            throws Exception {
        NetStartContentProvider provider = provider(query -> json("""
                {"movieList":[{"id":1525000,"nm":"年会不能停！2","sc":9.6,"showInfo":"今天有场次"}]}"""));

        // 公开热映列表只可用于发现影片 ID；完成详情补齐前不能写入真实内容。
        assertThat(provider.query(new ContentQuery(ContentResourceType.MOVIE, null, null, null))).isEmpty();
    }

    @Test
    void givenDetailWithoutExternalId_whenQuery_thenItIsQuarantinedInsteadOfMatchingByTitle() {
        NetStartContentProvider provider = provider(query -> json("""
                {"detailMovie":{"nm":"重名测试片","cat":"剧情","dur":"90分钟","sc":"8.0"}}"""));

        // 标题相同并不代表同一影片；空外部 ID 只能进入后续人工复核记录，不能生成幂等身份。
        assertThat(provider.query(movieDetail())).isEmpty();
    }

    @Test
    void givenCurrentCinemaSearchShape_whenQuery_thenItKeepsOnlyCinemaBasicFields() {
        NetStartContentProvider provider = provider(query -> json("""
                [{"id":41478,"info":{"name":"中影星空影城（榆垡店）","address":"大兴区康泰街26号"},
                "distance":"1836km","price":"33","tags":["座"]}]"""));

        ContentResult<List<? extends ContentItem>> result = provider.query(
                new ContentQuery(ContentResourceType.CINEMA, null, "430100", "影城")).orElseThrow();
        CinemaContent cinema = (CinemaContent) result.data().getFirst();
        assertThat(cinema.sourceCinemaId()).isEqualTo("41478");
        assertThat(cinema.cityCode()).isEqualTo("430100");
        assertThat(cinema.address()).isEqualTo("大兴区康泰街26号");
        assertThat(cinema.longitude()).isNull();
        assertThat(cinema.latitude()).isNull();
    }

    @Test
    void givenLegalStaticCoordinates_whenQuery_thenItKeepsCoordinatesWithoutUsingDistance() {
        NetStartContentProvider provider = provider(query -> json("""
                [{"id":41478,"lng":"112.9388","lat":"28.2282","distance":"100m",
                "info":{"name":"长沙测试影城","address":"长沙市芙蓉区测试路1号"}}]"""));

        CinemaContent cinema = (CinemaContent) provider.query(
                new ContentQuery(ContentResourceType.CINEMA, null, "430100", "影城")).orElseThrow().data().getFirst();

        // distance 是相对请求位置的临时值，不能进入内容模型；只保留上游明确给出的影院静态坐标。
        assertThat(cinema.longitude()).isEqualByComparingTo("112.9388");
        assertThat(cinema.latitude()).isEqualByComparingTo("28.2282");
    }

    @Test
    void givenConnectionFailure_whenQuery_thenItRetriesOnceAndReturnsTheSecondQualifiedResponse()
            throws Exception {
        AtomicInteger calls = new AtomicInteger();
        NetStartContentProvider provider = provider(query -> {
            if (calls.incrementAndGet() == 1) { throw new ResourceAccessException("offline"); }
            return json("{" + "\"detailMovie\":{\"id\":\"1\",\"nm\":\"测试片\",\"cat\":\"剧情\","
                    + "\"dur\":\"90分钟\",\"sc\":\"8.0\"}}");
        });

        assertThat(provider.query(movieDetail())).isPresent();
        assertThat(calls).hasValue(2);
    }

    @Test
    void givenTenRequestsInOneMinute_whenQueryAgain_thenLocalLimiterDoesNotCallNetStart() throws Exception {
        AtomicInteger calls = new AtomicInteger();
        NetStartContentProvider provider = provider(query -> {
            calls.incrementAndGet();
            return json("{" + "\"detailMovie\":{\"id\":\"1\",\"nm\":\"测试片\",\"cat\":\"剧情\","
                    + "\"dur\":\"90分钟\",\"sc\":\"8.0\"}}");
        });
        for (int index = 0; index < 10; index++) { assertThat(provider.query(movieDetail())).isPresent(); }

        assertThat(provider.query(movieDetail())).isEmpty();
        assertThat(calls).hasValue(10);
    }

    @Test
    void givenDailySync_whenHotListDetailsAndCinemaAreFetched_thenEveryRequestUsesTheSameLimit() throws Exception {
        AtomicInteger calls = new AtomicInteger();
        AtomicReference<String> cinemaCityCode = new AtomicReference<>();
        NetStartContentProvider provider = provider(query -> {
            calls.incrementAndGet();
            if (query.resourceType() == ContentResourceType.CINEMA) {
                cinemaCityCode.set(query.cityCode());
                return json("[{\"id\":41478,\"info\":{\"name\":\"测试影城一号\",\"address\":\"测试一区\"}},"
                        + "{\"id\":41479,\"info\":{\"name\":\"测试影城二号\",\"address\":\"测试二区\"}}]");
            }
            if (query.contentId() == null) {
                return json("{\"movieList\":[{\"id\":1},{\"id\":2},{\"id\":3},{\"id\":4},"
                        + "{\"id\":5},{\"id\":6},{\"id\":7},{\"id\":8},{\"id\":9},{\"id\":10}]}");
            }
            return json("{\"detailMovie\":{\"id\":" + query.contentId()
                    + ",\"nm\":\"测试片\",\"cat\":\"剧情\",\"dur\":\"90分钟\",\"sc\":\"8.0\"}}");
        });

        var batch = provider.fetchForDailySync();

        // 一轮保留热映列表、八部详情和影院共十次额度，不能因十部详情挤掉影院或越过本地限制。
        assertThat(calls).hasValue(10);
        assertThat(cinemaCityCode).hasValue("430100");
        assertThat(batch.contents()).hasSize(9);
        // 八部影片和两家影院都合格时，成功内容总数为十；不能因影院来自同一查询被误判字段不合格。
        assertThat(batch.attemptedCount()).isEqualTo(10);
        assertThat(batch.outcome())
                .isEqualTo(com.miaoyu.ticket.content.application.LiveContentSyncPort.Outcome.SUCCESS);
    }

    @Test
    void givenDailySyncHotListConnectionFailure_whenSynchronize_thenItCountsOneFailedContentItem() {
        NetStartContentProvider provider = provider(query -> {
            throw new ResourceAccessException("offline");
        });

        var batch = provider.fetchForDailySync();

        // 目录请求失败也必须进入内容项统计，才能让 ContentSyncService 写出 V004 要求的 FAILED 状态。
        assertThat(batch.contents()).isEmpty();
        assertThat(batch.attemptedCount()).isEqualTo(1);
        assertThat(batch.outcome())
                .isEqualTo(com.miaoyu.ticket.content.application.LiveContentSyncPort.Outcome.CONNECTION_FAILED);
    }

    @Test
    void givenMoreThanOneMinuteOfHotMovies_whenRecovering_thenItSkipsCompletedIdsAndUsesAtMostTenRequests()
            throws Exception {
        AtomicInteger calls = new AtomicInteger();
        NetStartContentProvider provider = provider(query -> {
            calls.incrementAndGet();
            if (query.contentId() == null) {
                return json("{\"movieList\":[{\"id\":1},{\"id\":2},{\"id\":3},{\"id\":4},"
                        + "{\"id\":5},{\"id\":6},{\"id\":7},{\"id\":8},{\"id\":9},{\"id\":10},{\"id\":11}]}" );
            }
            return json("{\"detailMovie\":{\"id\":" + query.contentId()
                    + ",\"nm\":\"测试片\",\"cat\":\"剧情\",\"dur\":\"90分钟\",\"sc\":\"8.0\"}}");
        });

        var batch = provider.fetchCurrentHotMovies(Set.of("1", "2"));

        // 目录占一次请求，剩下九次只给未完成详情；已完成身份不会再次占用额度。
        assertThat(calls).hasValue(10);
        assertThat(batch.contents()).hasSize(9);
        assertThat(batch.contents()).extracting(content -> ((MovieContent) content.result().data().getFirst())
                .sourceMovieId()).doesNotContain("1", "2");
        assertThat(batch.outcome()).isEqualTo(
                com.miaoyu.ticket.content.application.LiveContentSyncPort.Outcome.SUCCESS);
    }

    @Test
    void givenElevenMovies_whenTwoIncrementalRoundsRun_thenSecondRoundGetsTheRemainingMovies() {
        NetStartRawClient client = query -> {
            if (query.contentId() == null) {
                return json("{\"movieList\":[{\"id\":1},{\"id\":2},{\"id\":3},{\"id\":4},"
                        + "{\"id\":5},{\"id\":6},{\"id\":7},{\"id\":8},{\"id\":9},{\"id\":10},"
                        + "{\"id\":11}]}" );
            }
            return json("{\"detailMovie\":{\"id\":" + query.contentId()
                    + ",\"nm\":\"test\",\"cat\":\"drama\",\"dur\":\"90 minutes\",\"sc\":\"8.0\"}}");
        };
        var firstRound = provider(client).fetchCurrentHotMovies(Set.of());
        Set<String> savedIds = firstRound.contents().stream()
                .map(content -> ((MovieContent) content.result().data().getFirst()).sourceMovieId())
                .collect(java.util.stream.Collectors.toSet());

        // 新 Provider 实例代表下一分钟的独立同步轮次；已保存的九部不再占用详情预算。
        var secondRound = provider(client).fetchCurrentHotMovies(savedIds);

        assertThat(firstRound.contents()).hasSize(9);
        assertThat(secondRound.contents()).extracting(content ->
                ((MovieContent) content.result().data().getFirst()).sourceMovieId()).containsExactly("10", "11");
    }

    @Test
    void givenEightMovies_whenIncrementalSyncRuns_thenItStopsAfterTheDirectoryAndEightDetails() {
        AtomicInteger calls = new AtomicInteger();
        NetStartContentProvider provider = provider(query -> {
            calls.incrementAndGet();
            if (query.contentId() == null) {
                return json("{\"movieList\":[{\"id\":1},{\"id\":2},{\"id\":3},{\"id\":4},"
                        + "{\"id\":5},{\"id\":6},{\"id\":7},{\"id\":8}]}" );
            }
            return json("{\"detailMovie\":{\"id\":" + query.contentId()
                    + ",\"nm\":\"test\",\"cat\":\"drama\",\"dur\":\"90 minutes\",\"sc\":\"8.0\"}}");
        });

        var batch = provider.fetchCurrentHotMovies(Set.of());

        assertThat(calls).hasValue(9);
        assertThat(batch.contents()).hasSize(8);
    }

    @Test
    void givenRateLimitedDirectory_whenIncrementalSyncRuns_thenItDoesNotCreateMovieContent() {
        NetStartContentProvider provider = provider(query -> {
            throw HttpClientErrorException.create(HttpStatus.TOO_MANY_REQUESTS, "rate", null, null, null);
        });

        var batch = provider.fetchCurrentHotMovies(Set.of());

        assertThat(batch.contents()).isEmpty();
        assertThat(batch.attemptedCount()).isEqualTo(1);
        assertThat(batch.outcome()).isEqualTo(
                com.miaoyu.ticket.content.application.LiveContentSyncPort.Outcome.RATE_LIMITED);
    }

    @Test
    void givenSameReleaseState_whenIncrementalSync_thenItSkipsTheDetail() {
        AtomicInteger calls = new AtomicInteger();
        NetStartContentProvider provider = provider(query -> {
            calls.incrementAndGet();
            if (query.contentId() == null) {
                return json("{\"movieList\":[{\"id\":1,\"rt\":\"2026-08-01\",\"globalReleased\":true}]}");
            }
            return json("{\"detailMovie\":{\"id\":1,\"nm\":\"refresh\",\"cat\":\"drama\","
                    + "\"dur\":\"90 minutes\",\"sc\":\"8.0\"}}");
        });
        var batch = provider.fetchCurrentHotMovies(Map.of("1", new ContentPersistencePort.MovieState(
                "2026-08-01", "NOW_SHOWING", LocalDateTime.of(2026, 8, 3, 10, 0))));
        assertThat(calls).hasValue(1);
        assertThat(batch.contents()).isEmpty();
    }

    @Test
    void givenChangedReleaseState_whenIncrementalSync_thenItRefreshesTheDetail() {
        AtomicInteger calls = new AtomicInteger();
        NetStartContentProvider provider = provider(query -> {
            calls.incrementAndGet();
            if (query.contentId() == null) {
                return json("{\"movieList\":[{\"id\":1,\"rt\":\"2026-08-02\",\"globalReleased\":true}]}" );
            }
            return json("{\"detailMovie\":{\"id\":1,\"nm\":\"refresh\",\"cat\":\"drama\","
                    + "\"dur\":\"90 minutes\",\"sc\":\"8.0\"}}");
        });
        var batch = provider.fetchCurrentHotMovies(Map.of("1", new ContentPersistencePort.MovieState(
                "2026-08-01", "NOW_SHOWING", LocalDateTime.of(2026, 8, 6, 10, 0))));
        assertThat(calls).hasValue(2);
        assertThat(batch.contents()).hasSize(1);
    }

    @Test
    void givenChangedDirectoryMetadata_whenIncrementalSync_thenItRefreshesTheDetail() {
        AtomicInteger calls = new AtomicInteger();
        NetStartContentProvider provider = provider(query -> {
            calls.incrementAndGet();
            if (query.contentId() == null) {
                return json("{\"movieList\":[{\"id\":1,\"nm\":\"新版片名\",\"cat\":\"剧情\","
                        + "\"sc\":9.1,\"rt\":\"2026-08-01\",\"globalReleased\":true}]}");
            }
            return json("{\"detailMovie\":{\"id\":1,\"nm\":\"新版片名\",\"cat\":\"剧情\","
                    + "\"dur\":\"90分钟\",\"sc\":\"9.1\"}}");
        });

        var batch = provider.fetchCurrentHotMovies(Map.of("1", new ContentPersistencePort.MovieState(
                "旧片名", "[\"剧情\"]", new java.math.BigDecimal("8.0"), "2026-08-01", "NOW_SHOWING",
                LocalDateTime.of(2026, 8, 6, 10, 0))));

        assertThat(calls).hasValue(2);
        assertThat(batch.contents()).hasSize(1);
    }

    @Test
    void givenDirectoryMetadataIsMissingOrUsesSlashSeparator_whenIncrementalSync_thenItSkipsUnchangedDetail() {
        AtomicInteger calls = new AtomicInteger();
        NetStartContentProvider provider = provider(query -> {
            calls.incrementAndGet();
            return json("{\"movieList\":[{\"id\":1,\"nm\":\"原片名\",\"cat\":\"剧情/爱情\","
                    + "\"rt\":\"2026-08-01\",\"globalReleased\":true}]}");
        });

        var batch = provider.fetchCurrentHotMovies(Map.of("1", new ContentPersistencePort.MovieState(
                "原片名", "[\"剧情\",\"爱情\"]", new java.math.BigDecimal("8.0"), "2026-08-01", "NOW_SHOWING",
                LocalDateTime.of(2026, 8, 6, 10, 0))));

        // 目录未给评分时不能把未知值当成资料被删除；分类分隔符与详情 Mapper 一致后也不应重复拉详情。
        assertThat(calls).hasValue(1);
        assertThat(batch.contents()).isEmpty();
    }

    @Test
    void given429OrClientError_whenQuery_thenItDoesNotRetry() {
        AtomicInteger calls = new AtomicInteger();
        NetStartContentProvider provider = provider(query -> { calls.incrementAndGet();
            throw HttpClientErrorException.create(HttpStatus.TOO_MANY_REQUESTS, "rate", null, null, null); });
        assertThat(provider.query(movieDetail())).isEmpty();
        assertThat(calls).hasValue(1);
    }

    @Test
    void given5xx_whenQuery_thenItRetriesOnlyOnce() {
        AtomicInteger calls = new AtomicInteger();
        NetStartContentProvider provider = provider(query -> { calls.incrementAndGet();
            throw HttpServerErrorException.create(HttpStatus.BAD_GATEWAY, "upstream", null, null, null); });
        assertThat(provider.query(movieDetail())).isEmpty();
        assertThat(calls).hasValue(2);
    }

    @Test
    void givenDisabledOrNonLearningEnvironment_whenQuery_thenItNeverCallsProvider() {
        AtomicInteger calls = new AtomicInteger();
        NetStartRawClient rawClient = query -> {
            calls.incrementAndGet();
            return json("{}");
        };
        NetStartContentProvider disabled = new NetStartContentProvider(properties(false),
                new MockEnvironment().withProperty("spring.profiles.active", "dev"), CLOCK, rawClient);
        NetStartContentProvider production = new NetStartContentProvider(properties(true),
                new MockEnvironment().withProperty("spring.profiles.active", "prod"), CLOCK, rawClient);

        // 缺少显式开关或不在学习环境时只能走缓存、快照和 Demo，不能偷偷请求第三方。
        assertThat(disabled.query(movieDetail())).isEmpty();
        assertThat(production.query(movieDetail())).isEmpty();
        assertThat(calls).hasValue(0);
    }

    private NetStartContentProvider provider(NetStartRawClient rawClient) {
        return new NetStartContentProvider(properties(true),
                new MockEnvironment().withProperty("spring.profiles.active", "dev"), CLOCK, rawClient);
    }

    private NetStartProperties properties(boolean enabled) {
        return new NetStartProperties(enabled, false, "https://apis.netstart.cn/maoyan", "0 0 3 * * *",
                Duration.ofMillis(500), Duration.ofMillis(1500), 10, 1, Duration.ofMillis(1));
    }

    private ContentQuery movieDetail() { return new ContentQuery(ContentResourceType.MOVIE, 1L, null, null); }

    private com.fasterxml.jackson.databind.JsonNode json(String value) {
        try { return JSON.readTree(value); }
        catch (java.io.IOException exception) { throw new IllegalArgumentException(exception); }
    }
}
