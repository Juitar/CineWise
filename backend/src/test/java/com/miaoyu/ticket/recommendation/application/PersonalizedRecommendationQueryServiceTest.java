package com.miaoyu.ticket.recommendation.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.miaoyu.ticket.content.application.ContentQueryService;
import com.miaoyu.ticket.content.domain.ContentSource;
import com.miaoyu.ticket.content.domain.ContentSourceType;
import com.miaoyu.ticket.content.domain.MovieContent;
import com.miaoyu.ticket.recommendation.domain.RankedRecommendationCandidate;
import com.miaoyu.ticket.recommendation.domain.RecommendationConstraints;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;

class PersonalizedRecommendationQueryServiceTest {

    /*
     * 应用服务测试用 A 的公开批量端口夹具，不访问场次表、Mapper 或 Controller。
     * 内容资料只补齐 genres 和 rating，场次和票价始终来自批量端口的返回值。
     * 同时断言指标已经在最终结果处写入，防止出现只声明指标但运行时没有数据的问题。
     */
    @Test
    void shouldMergeContentAndShowtimeFactsThenRecordARecommendationResult() {
        RecommendationContentCandidateQueryService cinemas = Mockito.mock(
                RecommendationContentCandidateQueryService.class);
        ContentQueryService contents = Mockito.mock(ContentQueryService.class);
        RecommendationBatchShowtimeQueryPort showtimes = Mockito.mock(RecommendationBatchShowtimeQueryPort.class);
        Instant now = Instant.parse("2026-08-06T10:00:00Z");
        when(cinemas.listCinemas("430100")).thenReturn(List.of(
                new RecommendationContentCandidateQueryService.CinemaCandidate(20L, "测试影院", null, null,
                        "MOCK", LocalDateTime.ofInstant(now, ZoneOffset.UTC),
                        LocalDateTime.ofInstant(now.plusSeconds(3600), ZoneOffset.UTC), false)));
        RankedRecommendationCandidate show = new RankedRecommendationCandidate(
                "10", "20", "30", new BigDecimal("39.90"),
                now.plusSeconds(3600), now.plusSeconds(10800), List.of(), null, "TICKETING:MOCK", now,
                now.plusSeconds(60));
        var batchResult = new RecommendationBatchShowtimeQueryPort.BatchResult(
                List.of(show), false);
        when(showtimes.querySaleable(any(), any())).thenReturn(batchResult);
        MovieContent movie = new MovieContent(10L, "movie-10", "测试影片", "[\"喜剧\"]", 120, new BigDecimal("8.6"));
        when(contents.query(any())).thenReturn(new com.miaoyu.ticket.content.application.ContentResult<>(List.of(movie),
                new ContentSource("demo", ContentSourceType.MOCK), LocalDateTime.ofInstant(now, ZoneOffset.UTC),
                LocalDateTime.ofInstant(now.plusSeconds(3600), ZoneOffset.UTC), false, false, null));
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        PersonalizedRecommendationQueryService service = new PersonalizedRecommendationQueryService(
                cinemas, contents, showtimes, new RecommendationMetricsRecorder(registry), new ObjectMapper(),
                Clock.fixed(now, ZoneOffset.UTC));

        var constraints = new RecommendationConstraints(
                "430100", LocalDate.of(2026, 8, 6), 1, null,
                null, List.of("喜剧"), null, null, null, new BigDecimal("50.00"), List.of());
        var result = service.query(constraints);

        assertThat(result.plans()).singleElement().satisfies(plan -> {
            assertThat(plan.showId()).isEqualTo("30");
            assertThat(plan.price()).isEqualByComparingTo("39.90");
        });
        assertThat(registry.find("recommendation.result.total")
                .tags("algorithm_version", "rec-mvp-1", "candidate_source", "ticketing", "missing_factor", "none")
                .counter().count()).isEqualTo(1D);
    }
}
