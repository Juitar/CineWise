package com.miaoyu.ticket.agent.application.model;

/** 应用层隔离模型供应商 SDK、鉴权、HTTP 与原始输出的端口。 */
public interface ModelGateway {
    /** 未实现分类的网关不能扩大可调用范围，默认按普通对话处理。 */
    default AgentIntent classifyIntent(IntentClassificationRequest request) {
        return AgentIntent.GENERAL_CHAT;
    }

    PlanGenerationResponse generatePlan(PlanGenerationRequest request);

    ReplyGenerationResponse generateReply(ReplyGenerationRequest request);
}
