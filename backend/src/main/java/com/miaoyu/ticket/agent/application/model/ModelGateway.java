package com.miaoyu.ticket.agent.application.model;

/** 应用层隔离模型供应商 SDK、鉴权、HTTP 与原始输出的端口。 */
public interface ModelGateway {
    /**
     * 默认值保留给旧测试替身；真实和 Mock 网关必须覆盖并在异常时保守返回 GENERAL_CHAT。
     */
    default AgentIntent classifyIntent(IntentClassificationRequest request) {
        return AgentIntent.MOVIE;
    }

    PlanGenerationResponse generatePlan(PlanGenerationRequest request);

    ReplyGenerationResponse generateReply(ReplyGenerationRequest request);
}
