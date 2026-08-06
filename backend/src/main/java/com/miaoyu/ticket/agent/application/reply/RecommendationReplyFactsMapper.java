package com.miaoyu.ticket.agent.application.reply;

import com.miaoyu.ticket.agent.domain.tool.ToolResult;
import com.miaoyu.ticket.agent.domain.tool.ToolStatus;
import com.miaoyu.ticket.recommendation.domain.RecommendationPlanResult;
import java.time.Instant;
import java.util.Objects;

/**
 * 把 D 的公开结果收窄为回复模型需要的安全事实。
 *
 * <p>这里是 D 的 {@link RecommendationPlanResult} 与 B 回复事实之间的唯一转换点。新增 D 字段时，
 * 必须明确判断它是否适合给模型和展示层，不能让 D 的完整结果对象直接穿过主控。
 */
public final class RecommendationReplyFactsMapper {

    private RecommendationReplyFactsMapper() {
    }

    /**
     * 只有成功且包含业务数据的工具结果可以进入回复事实。
     *
     * <p>PROCESSING 的结果没有最终推荐，FAILED 的结果可能只有错误码；两者都必须由主控走进度或错误
     * 分支，不能在此处转换成空候选卡片。
     */
    public static RecommendationReplyFacts from(ToolResult<RecommendationPlanResult> toolResult, Instant now) {
        ToolResult<RecommendationPlanResult> result = Objects.requireNonNull(toolResult, "工具结果不能为空");
        Instant current = Objects.requireNonNull(now, "当前时间不能为空");
        if (result.status() != ToolStatus.SUCCESS || result.data() == null) {
            throw new IllegalArgumentException("只有成功推荐结果可以映射回复事实");
        }
        // 不补齐、不排序、不重算 D 的候选；B 只保留其已明确声明可展示的字段。
        RecommendationPlanResult data = result.data();
        return new RecommendationReplyFacts(
                data.algorithmVersion(),
                data.plans().stream()
                        .map(plan -> new RecommendationReplyCandidate(
                                plan.movieId(),
                                plan.cinemaId(),
                                plan.showId(),
                                plan.price().setScale(2).toPlainString(),
                                plan.startTime(),
                                plan.source(),
                                !plan.expiresAt().isAfter(current),
                                plan.purchaseEligible()))
                        .toList(),
                data.plans().stream().anyMatch(plan -> plan.purchaseEligible() && plan.expiresAt().isAfter(current)),
                data.missingFactors(),
                data.source(),
                data.dataAt(),
                data.expiresAt(),
                !data.expiresAt().isAfter(current),
                result.degraded());
    }

}
