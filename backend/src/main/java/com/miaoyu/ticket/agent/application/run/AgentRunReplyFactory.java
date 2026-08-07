package com.miaoyu.ticket.agent.application.run;

import com.miaoyu.ticket.agent.application.reply.AgentReplyMessageType;
import com.miaoyu.ticket.agent.application.reply.ErrorReplyFacts;
import com.miaoyu.ticket.agent.application.reply.ProgressReplyFacts;
import com.miaoyu.ticket.agent.application.reply.QuestionReplyFacts;
import com.miaoyu.ticket.agent.application.reply.RecommendationPlanCardFacts;
import com.miaoyu.ticket.agent.application.reply.RecommendationPlanCardFactsMapper;
import com.miaoyu.ticket.agent.application.reply.SelectSeatsReplyFacts;
import com.miaoyu.ticket.agent.application.reply.TravelAdviceCardFacts;
import com.miaoyu.ticket.agent.application.reply.TravelAdviceCardFactsMapper;
import com.miaoyu.ticket.agent.domain.tool.ToolResult;
import com.miaoyu.ticket.agent.domain.tool.ToolStatus;
import com.miaoyu.ticket.recommendation.domain.RecommendationPlanResult;
import com.miaoyu.ticket.ticketing.api.QueryShowsTool;
import com.miaoyu.ticket.ticketing.api.QueryShowsToolResult;
import com.miaoyu.ticket.travel.api.TravelAdviceToolResult;
import java.time.Instant;
import java.util.List;

/** 将多工具运行结果收窄为 B 自有的回复事实，隔离持久化层和业务 Owner DTO。 */
public final class AgentRunReplyFactory {
    private AgentRunReplyFactory() {
    }

    public static com.miaoyu.ticket.agent.application.model.ReplyGenerationResponse from(
            MultiToolSupervisorResult result, Instant now) {
        java.util.Objects.requireNonNull(now, "当前时间不能为空");
        if (!result.validation().isValid()) {
            return new com.miaoyu.ticket.agent.application.model.ReplyGenerationResponse(
                    "当前请求无法安全执行", AgentReplyMessageType.ERROR,
                    new ErrorReplyFacts(null, List.of("PLAN_REJECTED")));
        }
        if (result.safeNextAction() != null && result.safeNextAction().startsWith("QUESTION:")) {
            String missingSlot = result.safeNextAction().substring("QUESTION:".length());
            return new com.miaoyu.ticket.agent.application.model.ReplyGenerationResponse(
                    "请补充" + missingSlot + "。", AgentReplyMessageType.QUESTION,
                    new QuestionReplyFacts(missingSlot));
        }
        if (result.toolResults().stream().anyMatch(item -> item.result().status() == ToolStatus.PROCESSING)) {
            String nodeId = result.toolResults().stream()
                    .filter(item -> item.result().status() == ToolStatus.PROCESSING)
                    .map(MultiToolSupervisorResult.NodeToolResult::nodeId)
                    .findFirst().orElse("plan");
            return new com.miaoyu.ticket.agent.application.model.ReplyGenerationResponse(
                    "推荐节点仍在处理中。", AgentReplyMessageType.PROGRESS, new ProgressReplyFacts(nodeId));
        }
        SelectSeatsReplyFacts selectSeats = lastFreshShow(result, now);
        if (selectSeats != null) {
            return new com.miaoyu.ticket.agent.application.model.ReplyGenerationResponse(
                    "已确认场次，请前往选座。", AgentReplyMessageType.SELECT_SEATS, selectSeats);
        }
        ToolResult<TravelAdviceToolResult> travelAdvice = lastSuccessfulTravelAdvice(result);
        if (travelAdvice != null) {
            TravelAdviceCardFacts facts = TravelAdviceCardFactsMapper.from(travelAdvice);
            String text = facts.available() ? "已查询到出行建议。" : "该出行任务暂未生成建议。";
            return new com.miaoyu.ticket.agent.application.model.ReplyGenerationResponse(
                    text, AgentReplyMessageType.TRAVEL_ADVICE_CARD, facts);
        }
        ToolResult<RecommendationPlanResult> recommendation = lastSuccessfulRecommendation(result);
        if (recommendation != null) {
            RecommendationPlanCardFacts facts = RecommendationPlanCardFactsMapper.from(recommendation, now);
            String text = facts.plans().isEmpty()
                    ? "当前条件下暂无可购方案。"
                    : "已找到 " + facts.plans().size() + " 个可购方案。";
            return new com.miaoyu.ticket.agent.application.model.ReplyGenerationResponse(
                    text, AgentReplyMessageType.PLAN_CARD, facts);
        }
        ToolResult<?> failed = lastFailed(result);
        if (failed != null) {
            return new com.miaoyu.ticket.agent.application.model.ReplyGenerationResponse(
                    "当前工具暂时无法完成请求。", AgentReplyMessageType.ERROR,
                    new ErrorReplyFacts(failed.errorCode(), List.of("TOOL_FAILED")));
        }
        return new com.miaoyu.ticket.agent.application.model.ReplyGenerationResponse(
                "计划已保存，请等待下一步操作。", AgentReplyMessageType.PROGRESS,
                new ProgressReplyFacts("plan"));
    }

    /** 将多工具结果组装成持久化层可接收的 B 结果对象；D 类型依赖停留在应用映射边界。 */
    @SuppressWarnings("unchecked")
    public static MinimalReadOnlyAgentResult asMinimalResult(MultiToolSupervisorResult result, Instant now) {
        List<ToolResult<?>> toolResults = result.toolResults().stream()
                .map(MultiToolSupervisorResult.NodeToolResult::result)
                .toList();
        return new MinimalReadOnlyAgentResult(
                result.candidatePlan(), result.validation(), result.state(), toolResults, from(result, now));
    }

    /**
     * 选座入口只能来自本轮已成功的公开场次 Tool，并且整个结果窗口尚未过期。
     * 不读取座位图，也不把 A 的完整场次 DTO 写入回复或 SSE。
     */
    private static SelectSeatsReplyFacts lastFreshShow(MultiToolSupervisorResult result, Instant now) {
        for (int index = result.toolResults().size() - 1; index >= 0; index--) {
            MultiToolSupervisorResult.NodeToolResult nodeResult = result.toolResults().get(index);
            ToolResult<?> toolResult = nodeResult.result();
            if (!QueryShowsTool.TARGET_NAME.equals(nodeResult.targetName())
                    || toolResult.status() != ToolStatus.SUCCESS
                    || !toolResult.hasFreshnessWindow()
                    || !toolResult.expiresAt().isAfter(now)
                    || !(toolResult.data() instanceof QueryShowsToolResult shows)) {
                continue;
            }
            for (QueryShowsToolResult.ShowItem show : shows.shows()) {
                if (SelectSeatsReplyFacts.isPositiveLongDecimal(show.showId())
                        && SelectSeatsReplyFacts.isPositiveLongDecimal(show.movieId())
                        && SelectSeatsReplyFacts.isPositiveLongDecimal(show.cinemaId())) {
                    return new SelectSeatsReplyFacts(show.showId(), show.movieId(), show.cinemaId());
                }
            }
        }
        return null;
    }

    @SuppressWarnings("unchecked")
    private static ToolResult<RecommendationPlanResult> lastSuccessfulRecommendation(
            MultiToolSupervisorResult result) {
        for (int index = result.toolResults().size() - 1; index >= 0; index--) {
            ToolResult<?> item = result.toolResults().get(index).result();
            if (item.status() == ToolStatus.SUCCESS && item.data() instanceof RecommendationPlanResult) {
                return (ToolResult<RecommendationPlanResult>) item;
            }
        }
        return null;
    }

    private static ToolResult<?> lastFailed(MultiToolSupervisorResult result) {
        for (int index = result.toolResults().size() - 1; index >= 0; index--) {
            ToolResult<?> item = result.toolResults().get(index).result();
            if (item.status() == ToolStatus.FAILED) {
                return item;
            }
        }
        return null;
    }

    @SuppressWarnings("unchecked")
    private static ToolResult<TravelAdviceToolResult> lastSuccessfulTravelAdvice(MultiToolSupervisorResult result) {
        for (int index = result.toolResults().size() - 1; index >= 0; index--) {
            ToolResult<?> item = result.toolResults().get(index).result();
            if (item.status() == ToolStatus.SUCCESS && item.data() instanceof TravelAdviceToolResult) {
                return (ToolResult<TravelAdviceToolResult>) item;
            }
        }
        return null;
    }
}
