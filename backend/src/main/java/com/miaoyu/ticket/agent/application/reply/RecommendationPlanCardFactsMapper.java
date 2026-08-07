package com.miaoyu.ticket.agent.application.reply;

import com.miaoyu.ticket.agent.domain.tool.ToolResult;
import com.miaoyu.ticket.agent.domain.tool.ToolStatus;
import com.miaoyu.ticket.recommendation.domain.RecommendationPlan;
import com.miaoyu.ticket.recommendation.domain.RecommendationPlanResult;
import java.math.RoundingMode;
import java.time.Instant;

/** 将 D 的完整结果按已确认公开字段收窄为卡片事实，不重排、不补造方案。 */
public final class RecommendationPlanCardFactsMapper {
    private RecommendationPlanCardFactsMapper() { }
    public static RecommendationPlanCardFacts from(ToolResult<RecommendationPlanResult> toolResult, Instant now) {
        if (toolResult.status() != ToolStatus.SUCCESS || toolResult.data() == null) {
            throw new IllegalArgumentException("只有成功推荐结果可以映射卡片");
        }
        RecommendationPlanResult data = toolResult.data();
        boolean expired = !data.expiresAt().isAfter(now);
        return new RecommendationPlanCardFacts(data.schemaVersion(), data.algorithmVersion(), data.plans().stream()
                .map(plan -> item(plan, expired || !plan.expiresAt().isAfter(now))) .toList(),
                data.missingFactors(), data.relaxationSuggestion() == null ? null
                        : new RelaxationSuggestionFacts(data.relaxationSuggestion().factor().name(),
                                data.relaxationSuggestion().message()),
                data.usedProfile(), data.source(), data.dataAt(), data.expiresAt(), data.degraded(), expired);
    }
    private static RecommendationPlanCardItem item(RecommendationPlan plan, boolean expired) {
        return new RecommendationPlanCardItem(plan.planType().name(), plan.movieId(), plan.movieName(), plan.cinemaId(),
                plan.cinemaName(), plan.showId(), plan.price().setScale(2, RoundingMode.HALF_UP).toPlainString(),
                "CNY", plan.startTime(), plan.rating() == null ? null : plan.rating().toPlainString(), plan.score(),
                plan.reasons(),
                plan.source(), plan.dataAt(), plan.expiresAt(), expired, plan.purchaseEligible(),
                plan.distanceMeters());
    }
}
