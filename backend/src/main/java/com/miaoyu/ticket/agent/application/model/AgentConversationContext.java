package com.miaoyu.ticket.agent.application.model;

/**
 * 服务端保存的多轮语义上下文。
 *
 * <p>{@code originalRequest} 只帮助模型理解用户上一轮在谈什么，不能进入槽位快照或 Tool 参数；
 * {@code inheritedIntent} 只由服务端上一张 QUESTION 推导，前端和当前回答都不能自行指定。</p>
 */
public record AgentConversationContext(String originalRequest, AgentIntent inheritedIntent) {
    public AgentConversationContext {
        originalRequest = originalRequest == null || originalRequest.isBlank() ? null : originalRequest;
    }

    public static AgentConversationContext empty() {
        return new AgentConversationContext(null, null);
    }
}
