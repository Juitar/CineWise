package com.miaoyu.ticket.agent.application.reply;

/** 缺少下一项必填槽位时使用的最小追问事实。 */
public record QuestionReplyFacts(String missingSlot) implements AgentReplyPayload {

    public QuestionReplyFacts {
        if (missingSlot == null || missingSlot.isBlank()) {
            throw new IllegalArgumentException("missingSlot 不能为空");
        }
    }

    @Override
    public boolean supports(AgentReplyMessageType messageType) {
        return messageType == AgentReplyMessageType.QUESTION;
    }
}
