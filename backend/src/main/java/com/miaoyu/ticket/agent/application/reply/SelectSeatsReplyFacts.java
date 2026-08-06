package com.miaoyu.ticket.agent.application.reply;

/** 已由场次查询结果确认的选座入口事实。 */
public record SelectSeatsReplyFacts(String showId) implements AgentReplyPayload {
    public SelectSeatsReplyFacts {
        if (showId == null || showId.isBlank()) {
            throw new IllegalArgumentException("showId 不能为空");
        }
    }

    @Override
    public boolean supports(AgentReplyMessageType messageType) {
        return messageType == AgentReplyMessageType.SELECT_SEATS;
    }
}
