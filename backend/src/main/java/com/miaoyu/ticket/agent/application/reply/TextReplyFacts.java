package com.miaoyu.ticket.agent.application.reply;

/** 普通对话的受控回复事实，不包含实时业务数据、内部 ID 或工具参数。 */
public record TextReplyFacts() implements AgentReplyPayload {
    @Override
    public boolean supports(AgentReplyMessageType messageType) {
        return messageType == AgentReplyMessageType.TEXT;
    }
}
