package com.miaoyu.ticket.agent.application.reply;

/** 已由场次查询结果确认的选座入口事实。 */
public record SelectSeatsReplyFacts(String showId, String movieId, String cinemaId) implements AgentReplyPayload {
    public SelectSeatsReplyFacts {
        requireBusinessId("showId", showId);
        requireBusinessId("movieId", movieId);
        requireBusinessId("cinemaId", cinemaId);
    }

    public static boolean isPositiveLongDecimal(String value) {
        if (value == null || !value.matches("[1-9]\\d*")) {
            return false;
        }
        try {
            return Long.parseLong(value) > 0L;
        } catch (NumberFormatException exception) {
            return false;
        }
    }

    private static void requireBusinessId(String name, String value) {
        if (!isPositiveLongDecimal(value)) {
            throw new IllegalArgumentException(name + " 必须是 Java long 范围内无前导零的正十进制字符串");
        }
    }

    @Override
    public boolean supports(AgentReplyMessageType messageType) {
        return messageType == AgentReplyMessageType.SELECT_SEATS;
    }
}
