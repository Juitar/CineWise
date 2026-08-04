package com.miaoyu.ticket.agent.application.run;

import com.miaoyu.ticket.agent.application.model.ReplyGenerationResponse;
import com.miaoyu.ticket.agent.domain.plan.CandidatePlan;
import com.miaoyu.ticket.agent.domain.plan.PlanValidationResult;
import com.miaoyu.ticket.agent.domain.run.ExecutionRunState;
import com.miaoyu.ticket.agent.domain.tool.ToolResult;
import com.miaoyu.ticket.recommendation.application.FixedRecommendationResult;
import java.util.List;
import java.util.Objects;

/**
 * 一次最小只读请求返回的候选计划、运行快照、工具结果和内部回复。
 *
 * <p>这是应用层诊断结果，不是将来 REST 或 SSE 的直接返回体；其中 candidatePlan 和 toolResults
 * 可能需要在输出层进一步脱敏。调用方不能用它恢复写操作，也不能把内存 state 当作持久化运行记录。
 *
 * @param candidatePlan 本轮模型候选，便于审计服务端实际校验的输入
 * @param validationResult 候选的服务端校验结果，决定 state 是否存在
 * @param state 有效计划的最终内存快照；无效计划必须为 null
 * @param toolResults 本轮按执行顺序产生的只读工具结果不可变快照
 * @param reply 已按受控事实生成的内部回复，后续输出层仍需决定传输协议
 */
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
        // 这个不变量防止调用方把无效计划伪装成已初始化运行，或遗漏有效计划的状态诊断信息。
        if (validationResult.isValid() != (state != null)) {
            throw new IllegalArgumentException("有效计划必须有运行快照，无效计划不能有运行快照");
        }
    }
}
