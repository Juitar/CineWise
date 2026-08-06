package com.miaoyu.ticket.recommendation.application;

import static org.assertj.core.api.Assertions.assertThat;

import com.miaoyu.ticket.recommendation.domain.RecommendationPlanResult;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;

class RecommendationMetricsRecorderTest {

    /*
     * 指标测试只验证受控标签和计数，不需要启动 Web、Agent 或真实监控后端。
     * 使用空方案覆盖正常业务降级，确保不会把它统计为失败。
     * 测试中故意传入 traceId 风格的来源后缀，验证不会出现在指标标签中。
     */
    @Test
    void shouldRecordEmptyAndDegradedRatesWithSanitizedTags() {
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        RecommendationMetricsRecorder recorder = new RecommendationMetricsRecorder(registry);
        RecommendationPlanResult result = new RecommendationPlanResult(
                "rec-mvp-1", List.of(), List.of("SHOWTIME", "RATING", "untrusted"), null,
                "TICKETING:trace-should-not-be-a-tag", Instant.parse("2026-08-06T00:00:00Z"),
                Instant.parse("2026-08-06T00:01:00Z"), true);

        recorder.record(result);

        assertThat(registry.find("recommendation.result.total")
                .tags("algorithm_version", "rec-mvp-1", "candidate_source", "ticketing",
                        "missing_factor", "rating_showtime").counter().count()).isEqualTo(1D);
        assertThat(registry.find("recommendation.result.empty")
                .tags("algorithm_version", "rec-mvp-1", "candidate_source", "ticketing",
                        "missing_factor", "rating_showtime").counter().count()).isEqualTo(1D);
        assertThat(registry.find("recommendation.result.degraded")
                .tags("algorithm_version", "rec-mvp-1", "candidate_source", "ticketing",
                        "missing_factor", "rating_showtime").counter().count()).isEqualTo(1D);
    }

    @Test
    void shouldOnlyIncrementTotalForAUsableRecommendationResult() {
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        RecommendationMetricsRecorder recorder = new RecommendationMetricsRecorder(registry);
        RecommendationPlanResult result = new RecommendationPlanResult(
                "rec-mvp-1", List.of(), List.of(), null, "unexpected-provider", Instant.parse("2026-08-06T00:00:00Z"),
                Instant.parse("2026-08-06T00:01:00Z"), false);

        recorder.record(result);

        assertThat(registry.find("recommendation.result.total")
                .tags("algorithm_version", "rec-mvp-1", "candidate_source", "unknown",
                        "missing_factor", "none").counter().count()).isEqualTo(1D);
        assertThat(registry.find("recommendation.result.degraded").counter()).isNull();
    }
}
