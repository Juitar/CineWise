package com.miaoyu.ticket.agent.application.reply;

import java.time.Instant;

/** 从 D 的已校验结果映射出的可展示候选，不包含座位、库存或用户身份。 */
public record RecommendationReplyCandidate(
        String movieId,
        String cinemaId,
        String showId,
        String price,
        Instant startTime,
        String source,
        boolean expired,
        boolean purchaseEligible) {

    public RecommendationReplyCandidate {
        requireText(movieId, "movieId");
        requireText(cinemaId, "cinemaId");
        requireText(source, "source");
        if (purchaseEligible && (isBlank(showId) || isBlank(price) || startTime == null)) {
            throw new IllegalArgumentException("可购回复候选必须包含场次、价格和开场时间");
        }
    }

    private static void requireText(String value, String fieldName) {
        if (isBlank(value)) {
            throw new IllegalArgumentException(fieldName + " 不能为空");
        }
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
