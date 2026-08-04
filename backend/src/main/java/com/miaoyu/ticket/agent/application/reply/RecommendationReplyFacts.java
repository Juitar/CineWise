package com.miaoyu.ticket.agent.application.reply;

import java.time.Instant;
import java.util.List;
import java.util.Objects;

/**
 * 回复模型可使用的推荐事实，保留 D 的来源、时效、缺失因素和降级标记。
 *
 * <p>保留这些元数据的目的，是让展示层和模型文案都不能把缓存、过期或无场次结果包装成实时可购承诺。
 * 本类不增加任何由 B 重新计算的推荐结论，D 的业务判断仍以其公开结果为准。
 *
 * @param algorithmVersion D 返回的算法版本，用于结果追溯，不应成为用户身份画像
 * @param candidates 可展示候选的不可变快照，不能在回复生成后追加虚构候选
 * @param purchaseEligible 当前是否存在可购场次，决定使用 PLAN_CARD 或 MOVIE_CARD
 * @param missingFactors 无法满足可购条件的因素，例如 SHOWTIME，不能以空数组替代失败原因
 * @param source D 提供的数据来源说明
 * @param dataAt 数据产生时间，用于判断展示事实的时效
 * @param expiresAt 数据过期时间，必须晚于 dataAt
 * @param expired D 已判定的过期标记，B 不自行覆盖
 * @param degraded D 已判定的降级标记，B 不把它转换为工具执行失败
 */
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
        // 无有效时间区间的推荐无法诚实地向用户说明时效，必须在边界处拒绝。
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
