package com.miaoyu.ticket.agent.application.run;

import com.miaoyu.ticket.agent.application.model.ReplyGenerationResponse;
import com.miaoyu.ticket.agent.domain.plan.CandidatePlan;
import com.miaoyu.ticket.agent.domain.plan.PlanValidationResult;
import com.miaoyu.ticket.agent.domain.run.ExecutionRunState;
import com.miaoyu.ticket.agent.domain.tool.ToolResult;
import com.miaoyu.ticket.recommendation.application.FixedRecommendationResult;
import java.util.List;
import java.util.Objects;

/** 一次最小只读请求返回的候选计划、运行快照、工具结果和内部回复。 */
public record MinimalReadOnlyAgentResult(
        CandidatePlan candidatePlan,
        PlanValidationResult validationResult,
        ExecutionRunState state,
        List<ToolResult<FixedRecommendationResult>> toolResults,
        ReplyGenerationResponse reply) {

    public MinimalReadOnlyAgentResult {
        candidatePlan = Objects.requireNonNull(candidatePlan, "candidatePlan 不能为空");
        validationResult = Objects.requireNonNull(validationResult, "validationResult 不能为空");
        toolResults = List.copyOf(Objects.requireNonNull(toolResults, "toolResults 不能为空"));
        reply = Objects.requireNonNull(reply, "reply 不能为空");
        if (validationResult.isValid() != (state != null)) {
            throw new IllegalArgumentException("有效计划必须有运行快照，无效计划不能有运行快照");
        }
    }
}
