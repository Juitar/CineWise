package com.miaoyu.ticket.recommendation.domain;

import java.time.Instant;
import java.util.List;
import java.util.Objects;

/** 推荐计算的最终结果，空方案仍是正常业务结果而不是虚构场次。 */
public record RecommendationPlanResult(
        String algorithmVersion, List<RecommendationPlan> plans, List<String> missingFactors,
        RelaxationSuggestion relaxationSuggestion, String source, Instant dataAt, Instant expiresAt, boolean degraded) {
    public RecommendationPlanResult {
        if (algorithmVersion == null || algorithmVersion.isBlank() || source == null || source.isBlank()) {
            throw new IllegalArgumentException("版本和来源不能为空");
        }
        plans = List.copyOf(Objects.requireNonNull(plans, "plans 不能为空"));
        missingFactors = List.copyOf(Objects.requireNonNull(missingFactors, "missingFactors 不能为空"));
        dataAt = Objects.requireNonNull(dataAt, "dataAt 不能为空");
        expiresAt = Objects.requireNonNull(expiresAt, "expiresAt 不能为空");
        if (!expiresAt.isAfter(dataAt) || plans.size() > 3) {
            throw new IllegalArgumentException("推荐结果时效或数量不合法");
        }
    }
}
