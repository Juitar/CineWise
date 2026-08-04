package com.miaoyu.ticket.agent.application.reply;

import java.time.Instant;

/**
 * 从 D 的已校验结果映射出的可展示候选，不包含座位、库存或用户身份。
 *
 * <p>候选仅供展示和引导下一步，不是可直接提交的购票命令。即使 purchaseEligible 为 true，后续交易
 * 仍必须由 A 重新校验场次、价格和座位，不能相信本次推荐的快照。
 *
 * @param movieId D 返回的影片标识，展示层不得替换为用户输入猜测的影片
 * @param cinemaId D 返回的影院标识，不承载用户身份或地理位置原文
 * @param showId 可购时必填的场次标识；无场次降级时允许为空
 * @param price 可购时必填的展示价格；不是后续下单的服务端定价依据
 * @param startTime 可购时必填的开场时间；无场次降级时允许为空
 * @param source D 标记的数据来源，帮助展示层避免把降级数据说成实时数据
 * @param expired D 标记的时效状态，不能因文案润色而丢失
 * @param purchaseEligible D 判定的当前可购标记，不保证用户随后仍可购买
 */
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
        // 可购卡缺少任何交易定位信息都无法安全跳转；无场次卡则不能伪造这些字段。
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
