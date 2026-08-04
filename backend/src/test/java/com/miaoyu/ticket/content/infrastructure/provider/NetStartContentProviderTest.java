package com.miaoyu.ticket.content.infrastructure.provider;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.miaoyu.ticket.content.application.ContentQuery;
import com.miaoyu.ticket.content.application.ContentResult;
import com.miaoyu.ticket.content.domain.ContentItem;
import com.miaoyu.ticket.content.domain.ContentResourceType;
import com.miaoyu.ticket.content.domain.ContentSourceType;
import com.miaoyu.ticket.content.domain.MovieContent;
import com.miaoyu.ticket.content.domain.CinemaContent;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
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
                "showInfo":"今天282家影院放映1596场","commentedUsers":123}}"""));

        ContentResult<List<? extends ContentItem>> result = provider.query(movieDetail()).orElseThrow();

        MovieContent movie = (MovieContent) result.data().getFirst();
        // 上游的场次数、影评等字段只能证明其存在，不能穿透为 A 的票务事实或 D 的内容字段。
        assertThat(movie.sourceMovieId()).isEqualTo("1525000");
        assertThat(movie.durationMinutes()).isEqualTo(118);
        assertThat(result.source().type()).isEqualTo(ContentSourceType.LIVE);
        assertThat(result.degraded()).isFalse();
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
                new ContentQuery(ContentResourceType.CINEMA, null, "1", "影城")).orElseThrow();
        CinemaContent cinema = (CinemaContent) result.data().getFirst();
        assertThat(cinema.sourceCinemaId()).isEqualTo("41478");
        assertThat(cinema.address()).isEqualTo("大兴区康泰街26号");
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
        return new NetStartProperties(enabled, "https://apis.netstart.cn/maoyan", "0 0 3 * * *",
                Duration.ofMillis(500), Duration.ofMillis(1500), 10, 1, Duration.ofMillis(1));
    }

    private ContentQuery movieDetail() { return new ContentQuery(ContentResourceType.MOVIE, 1L, null, null); }

    private com.fasterxml.jackson.databind.JsonNode json(String value) {
        try { return JSON.readTree(value); }
        catch (java.io.IOException exception) { throw new IllegalArgumentException(exception); }
    }
}
