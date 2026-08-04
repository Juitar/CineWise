package com.miaoyu.ticket.agent.application.reply;

import java.time.Instant;
import java.util.List;
import java.util.Objects;

/** 回复模型可使用的推荐事实，保留 D 的来源、时效、缺失因素和降级标记。 */
public record RecommendationReplyFacts(
        String algorithmVersion,
        List<RecommendationReplyCandidate> candidates,
        boolean purchaseEligible,
        List<String> missingFactors,
        String source,
        Instant dataAt,
        Instant expiresAt,
        boolean expired,
        boolean degraded) implements AgentReplyPayload {

    public RecommendationReplyFacts {
        requireText(algorithmVersion, "algorithmVersion");
        candidates = List.copyOf(Objects.requireNonNull(candidates, "candidates 不能为空"));
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
        return purchaseEligible
                ? messageType == AgentReplyMessageType.PLAN_CARD
                : messageType == AgentReplyMessageType.MOVIE_CARD;
    }

    private static void requireText(String value, String fieldName) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(fieldName + " 不能为空");
        }
    }
}
