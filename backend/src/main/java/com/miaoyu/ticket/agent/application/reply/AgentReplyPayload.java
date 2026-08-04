package com.miaoyu.ticket.agent.application.reply;

/** 回复模型只接收这些 B 自有的类型化事实，不接收任意动态字段。 */
public sealed interface AgentReplyPayload
        permits ErrorReplyFacts, ProgressReplyFacts, QuestionReplyFacts, RecommendationReplyFacts {

    /** 判断当前载荷是否允许用于指定回复类型。 */
    boolean supports(AgentReplyMessageType messageType);
}
