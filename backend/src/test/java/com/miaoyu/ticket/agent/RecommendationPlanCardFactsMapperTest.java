package com.miaoyu.ticket.agent;

import static org.assertj.core.api.Assertions.assertThat;

import com.miaoyu.ticket.agent.application.reply.RecommendationPlanCardFacts;
import com.miaoyu.ticket.agent.application.reply.RecommendationPlanCardFactsMapper;
import com.miaoyu.ticket.agent.application.reply.RecommendationPlanCardItem;
import com.miaoyu.ticket.agent.domain.tool.ToolResult;
import com.miaoyu.ticket.agent.domain.tool.ToolStatus;
import com.miaoyu.ticket.recommendation.domain.RecommendationEvidence;
import com.miaoyu.ticket.recommendation.domain.RecommendationPlan;
import com.miaoyu.ticket.recommendation.domain.RecommendationPlanResult;
import com.miaoyu.ticket.recommendation.domain.RelaxationSuggestion;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;

/** 完整推荐卡片只能映射 D 已公开的展示字段。 */
class RecommendationPlanCardFactsMapperTest {
    private static final Instant DATA_AT = Instant.parse("2026-08-07T02:00:00Z");
    private static final Instant EXPIRES_AT = DATA_AT.plusSeconds(300);

    @Test
    void shouldMapPublicPlanFieldsWithoutEvidenceOrTravelDetails() {
        RecommendationPlanCardFacts facts = RecommendationPlanCardFactsMapper.from(
                success(List.of(plan()), null), DATA_AT);

        assertThat(facts.schemaVersion()).isEqualTo("1.0");
        assertThat(facts.algorithmVersion()).isEqualTo("rec-mvp-1");
        assertThat(facts.usedProfile()).isTrue();
        assertThat(facts.source()).isEqualTo("D:FIXTURE");
        assertThat(facts.dataAt()).isEqualTo(DATA_AT);
        assertThat(facts.expiresAt()).isEqualTo(EXPIRES_AT);
        assertThat(facts.plans()).singleElement().satisfies(item -> {
            assertThat(item.planType()).isEqualTo("NEAREST");
            assertThat(item.distanceMeters()).isEqualTo(860);
            assertThat(item.price()).isEqualTo("45.00");
        });
        assertThat(RecommendationPlanCardItem.class.getRecordComponents())
                .extracting(component -> component.getName())
                .doesNotContain("evidence", "latitude", "longitude", "address", "estimatedTravelMinutes");
    }

    @Test
    void shouldKeepEmptyPlanAndRelaxationSuggestionAsPlanCardFacts() {
        RelaxationSuggestion relaxation = new RelaxationSuggestion(
                RelaxationSuggestion.Factor.BUDGET, "可放宽预算");
        RecommendationPlanCardFacts facts = RecommendationPlanCardFactsMapper.from(
                success(List.of(), relaxation), DATA_AT);

        assertThat(facts.plans()).isEmpty();
        assertThat(facts.relaxationSuggestion()).isNotNull();
        assertThat(facts.relaxationSuggestion().factor()).isEqualTo("BUDGET");
        assertThat(facts.relaxationSuggestion().message()).isEqualTo("可放宽预算");
    }

    @Test
    void shouldMarkExpiredResultAndPlan() {
        RecommendationPlanCardFacts facts = RecommendationPlanCardFactsMapper.from(
                success(List.of(plan()), null), EXPIRES_AT.plusSeconds(1));

        assertThat(facts.expired()).isTrue();
        assertThat(facts.plans()).singleElement().extracting(RecommendationPlanCardItem::expired).isEqualTo(true);
    }

    private static ToolResult<RecommendationPlanResult> success(
            List<RecommendationPlan> plans, RelaxationSuggestion relaxation) {
        RecommendationPlanResult result = new RecommendationPlanResult(
                "1.0", "rec-mvp-1", plans, List.of("SHOWTIME"), relaxation, true, "D:FIXTURE", DATA_AT,
                EXPIRES_AT, plans.isEmpty());
        return new ToolResult<>(ToolStatus.SUCCESS, result, null, false, false, "RENDER_RESULT", result.degraded(),
                null, 1L, DATA_AT, EXPIRES_AT);
    }

    private static RecommendationPlan plan() {
        return new RecommendationPlan(
                RecommendationPlan.PlanType.NEAREST, "10001", "影片", "20001", "影院", "30001",
                new BigDecimal("45.00"), DATA_AT.plusSeconds(3600), new BigDecimal("8.5"), 860, 12, 0.91D,
                List.of("距离最近"), List.of(new RecommendationEvidence(
                        "showtime", "30001", "A:FIXTURE", DATA_AT, EXPIRES_AT)), "D:FIXTURE", DATA_AT,
                EXPIRES_AT, true);
    }
}
