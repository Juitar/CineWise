package com.miaoyu.ticket.agent.application.model;

/** 应用层隔离模型供应商 SDK、鉴权、HTTP 与原始输出的端口。 */
public interface ModelGateway {
    PlanGenerationResponse generatePlan(PlanGenerationRequest request);

    ReplyGenerationResponse generateReply(ReplyGenerationRequest request);
}
