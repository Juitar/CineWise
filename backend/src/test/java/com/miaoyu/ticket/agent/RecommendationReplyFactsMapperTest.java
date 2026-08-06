package com.miaoyu.ticket.agent;

import static org.assertj.core.api.Assertions.assertThat;

import com.miaoyu.ticket.agent.application.reply.RecommendationReplyFacts;
import com.miaoyu.ticket.agent.application.reply.RecommendationReplyFactsMapper;
import com.miaoyu.ticket.agent.domain.tool.ToolResult;
import com.miaoyu.ticket.agent.domain.tool.ToolStatus;
import com.miaoyu.ticket.recommendation.domain.RecommendationEvidence;
import com.miaoyu.ticket.recommendation.domain.RecommendationPlan;
import com.miaoyu.ticket.recommendation.domain.RecommendationPlanResult;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;

/** 验证 D 的公开推荐结果只以 B 已声明的安全字段进入回复事实。 */
class RecommendationReplyFactsMapperTest {
    private static final Instant DATA_AT = Instant.parse("2026-08-04T00:00:00Z");
    private static final Instant EXPIRES_AT = DATA_AT.plusSeconds(1_800);

    @Test
    void shouldMapFreshPurchasePlanWithoutLeakingOptionalDisplayFields() {
        RecommendationReplyFacts facts = RecommendationReplyFactsMapper.from(
                success(List.of(plan(EXPIRES_AT, true)), List.of(), false), DATA_AT.plusSeconds(60));

        assertThat(facts.purchaseEligible()).isTrue();
        assertThat(facts.degraded()).isFalse();
        assertThat(facts.expired()).isFalse();
        assertThat(facts.candidates()).singleElement().satisfies(candidate -> {
            assertThat(candidate.movieId()).isEqualTo("101");
            assertThat(candidate.cinemaId()).isEqualTo("201");
            assertThat(candidate.showId()).isEqualTo("301");
            assertThat(candidate.price()).isEqualTo("45.00");
            assertThat(candidate.startTime()).isEqualTo(DATA_AT.plusSeconds(600));
        });
    }

    @Test
    void shouldMapEmptyResultAsSuccessfulDegradedMovieCardFacts() {
        RecommendationReplyFacts facts = RecommendationReplyFactsMapper.from(
                success(List.of(), List.of("SHOWTIME"), true), DATA_AT.plusSeconds(60));

        assertThat(facts.candidates()).isEmpty();
        assertThat(facts.purchaseEligible()).isFalse();
        assertThat(facts.missingFactors()).containsExactly("SHOWTIME");
        assertThat(facts.degraded()).isTrue();
        assertThat(facts.expired()).isFalse();
    }

    @Test
    void shouldKeepDDegradationAndNotInventPurchaseEligibility() {
        RecommendationReplyFacts facts = RecommendationReplyFactsMapper.from(
                success(List.of(plan(EXPIRES_AT, false)), List.of("DISTANCE"), true), DATA_AT.plusSeconds(60));

        assertThat(facts.candidates()).singleElement().satisfies(candidate ->
                assertThat(candidate.purchaseEligible()).isFalse());
        assertThat(facts.purchaseEligible()).isFalse();
        assertThat(facts.degraded()).isTrue();
        assertThat(facts.missingFactors()).containsExactly("DISTANCE");
    }

    @Test
    void shouldMarkExpiredPlansAsUnavailableAtRenderTime() {
        Instant expiredAt = DATA_AT.plusSeconds(300);
        RecommendationReplyFacts facts = RecommendationReplyFactsMapper.from(
                success(List.of(plan(expiredAt, true)), List.of(), false), expiredAt);

        assertThat(facts.candidates()).singleElement().satisfies(candidate ->
                assertThat(candidate.expired()).isTrue());
        assertThat(facts.purchaseEligible()).isFalse();
        assertThat(facts.expired()).isFalse();
    }

    private static ToolResult<RecommendationPlanResult> success(
            List<RecommendationPlan> plans, List<String> missingFactors, boolean degraded) {
        RecommendationPlanResult result = new RecommendationPlanResult(
                "1.0", "recommendation-v1", plans, missingFactors, null, false, "D:FIXTURE",
                DATA_AT, EXPIRES_AT, degraded);
        return new ToolResult<>(ToolStatus.SUCCESS, result, null, false, false, "RENDER_RESULT", degraded,
                degraded ? "RECOMMENDATION_DEGRADED" : null, 1L, DATA_AT, EXPIRES_AT);
    }

    private static RecommendationPlan plan(Instant expiresAt, boolean purchaseEligible) {
        return new RecommendationPlan(
                RecommendationPlan.PlanType.COMPREHENSIVE, "101", "测试影片", "201", "测试影院", "301",
                new BigDecimal("45.00"), DATA_AT.plusSeconds(600), new BigDecimal("9.1"), 800, 12, 0.9D,
                List.of("匹配偏好"), List.of(new RecommendationEvidence(
                        "showtime", "301", "A:TICKETING", DATA_AT, expiresAt)),
                "D:FIXTURE", DATA_AT, expiresAt, purchaseEligible);
    }
}
