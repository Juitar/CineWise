package com.miaoyu.ticket.agent.application.reply;

/** 只读工具仍在处理时返回的安全进度事实。 */
public record ProgressReplyFacts(String nodeId) implements AgentReplyPayload {

    public ProgressReplyFacts {
        if (nodeId == null || nodeId.isBlank()) {
            throw new IllegalArgumentException("nodeId 不能为空");
        }
    }

    @Override
    public boolean supports(AgentReplyMessageType messageType) {
        return messageType == AgentReplyMessageType.PROGRESS;
    }
}
