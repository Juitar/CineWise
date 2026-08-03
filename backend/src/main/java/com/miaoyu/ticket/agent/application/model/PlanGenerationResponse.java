package com.miaoyu.ticket.agent.application.model;

import com.miaoyu.ticket.agent.domain.plan.CandidatePlan;
import com.miaoyu.ticket.agent.domain.plan.PlanValidationResult;
import java.util.Objects;

/** 模型候选计划及其服务端校验结果。 */
public record PlanGenerationResponse(CandidatePlan candidatePlan, PlanValidationResult validationResult) {

    public PlanGenerationResponse {
        Objects.requireNonNull(candidatePlan, "candidatePlan 不能为空");
        Objects.requireNonNull(validationResult, "validationResult 不能为空");
    }
}
