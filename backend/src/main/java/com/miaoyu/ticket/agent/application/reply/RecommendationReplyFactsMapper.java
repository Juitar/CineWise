package com.miaoyu.ticket.agent.application.reply;

import com.miaoyu.ticket.agent.domain.tool.ToolResult;
import com.miaoyu.ticket.agent.domain.tool.ToolStatus;
import com.miaoyu.ticket.recommendation.application.FixedRecommendationResult;
import java.util.Objects;

/** 把 D 的公开结果收窄为回复模型需要的安全事实。 */
public final class RecommendationReplyFactsMapper {

    private RecommendationReplyFactsMapper() {
    }

    /** 只有成功且包含业务数据的工具结果可以进入回复事实。 */
    public static RecommendationReplyFacts from(ToolResult<FixedRecommendationResult> toolResult) {
        ToolResult<FixedRecommendationResult> result = Objects.requireNonNull(toolResult, "工具结果不能为空");
        if (result.status() != ToolStatus.SUCCESS || result.data() == null) {
            throw new IllegalArgumentException("只有成功推荐结果可以映射回复事实");
        }
        FixedRecommendationResult data = result.data();
        return new RecommendationReplyFacts(
                data.algorithmVersion(),
                data.candidates().stream()
                        .map(candidate -> new RecommendationReplyCandidate(
                                candidate.movieId(),
                                candidate.cinemaId(),
                                candidate.showId(),
                                candidate.price(),
                                candidate.startTime(),
                                candidate.source(),
                                candidate.isExpired(),
                                candidate.purchaseEligible()))
                        .toList(),
                data.purchaseEligible(),
                data.missingFactors(),
                data.source(),
                data.dataAt(),
                data.expiresAt(),
                data.isExpired(),
                result.degraded());
    }
}
