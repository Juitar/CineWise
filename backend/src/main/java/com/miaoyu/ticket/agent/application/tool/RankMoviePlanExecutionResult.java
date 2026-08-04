package com.miaoyu.ticket.agent.application.tool;

import com.miaoyu.ticket.agent.domain.run.ExecutionRunState;
import com.miaoyu.ticket.agent.domain.tool.ToolResult;
import com.miaoyu.ticket.recommendation.application.FixedRecommendationResult;
import java.util.Objects;

/** 一次推荐工具调用后的运行快照和原始类型化结果。 */
public record RankMoviePlanExecutionResult(
        ExecutionRunState state, ToolResult<FixedRecommendationResult> toolResult) {

    public RankMoviePlanExecutionResult {
        state = Objects.requireNonNull(state, "运行状态不能为空");
        toolResult = Objects.requireNonNull(toolResult, "工具结果不能为空");
    }
}
