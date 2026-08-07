package com.miaoyu.ticket.agent.application.reply;

import java.time.Instant;
import java.util.List;
import java.util.Objects;

/** 完整推荐唯一允许进入 PLAN_CARD 的公开字段，不保留 D 的候选证据或位置上下文。 */
public record RecommendationPlanCardFacts(
        String schemaVersion, String algorithmVersion, List<RecommendationPlanCardItem> plans,
        List<String> missingFactors, RelaxationSuggestionFacts relaxationSuggestion, boolean usedProfile,
        String source, Instant dataAt, Instant expiresAt, boolean degraded, boolean expired)
        implements AgentReplyPayload {
    public RecommendationPlanCardFacts {
        requireText(schemaVersion, "schemaVersion");
        requireText(algorithmVersion, "algorithmVersion");
        plans = List.copyOf(Objects.requireNonNull(plans, "plans 不能为空"));
        missingFactors = List.copyOf(Objects.requireNonNull(missingFactors, "missingFactors 不能为空"));
        requireText(source, "source");
        dataAt = Objects.requireNonNull(dataAt, "dataAt 不能为空");
        expiresAt = Objects.requireNonNull(expiresAt, "expiresAt 不能为空");
        if (!expiresAt.isAfter(dataAt)) {
            throw new IllegalArgumentException("expiresAt 必须晚于 dataAt");
        }
    }
    @Override
    public boolean supports(AgentReplyMessageType messageType) {
        return messageType == AgentReplyMessageType.PLAN_CARD;
    }
    private static void requireText(String value, String name) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(name + " 不能为空");
        }
    }
}
