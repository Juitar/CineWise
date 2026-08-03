package com.miaoyu.ticket.recommendation.application;

import java.util.Objects;
import java.time.Instant;

/**
 * 推荐结果中的内容候选。
 *
 * <p>第 4.1 至 4.3 只允许返回影片和影院引用。showId、价格和开场时间保持为空，明确表示这不是可购
 * 方案，也不能被 B 组装为 PLAN_CARD。</p>
 */
public record RecommendationCandidate(
        String movieId,
        String cinemaId,
        String showId,
        String price,
        Instant startTime,
        String source,
        boolean isExpired,
        boolean purchaseEligible) {

    /*
     * 可购候选必须完整引用 A 的动态场次事实。
     * 不可购候选只保留影片和影院内容引用。
     * 这两种结果不能混淆，否则会错误触发购票流程。
     * D 不为缺失字段生成默认值。
     * source 用于页面标识数据来源。
     * isExpired 表示候选不可继续用于购票。
     * price 只接受 A 已确认的值。
     * startTime 只接受 A 已确认的值。
     * showId 只接受 A 已确认的值。
     * movieId 用于关联用户选择的影片。
     * cinemaId 用于关联用户选择的影院。
     * 候选不能替代建单前校验。
     */

    /** 候选必须可追溯到来源，不能把固定 Demo 数据伪装成实时票务数据。 */
    public RecommendationCandidate {
        // ID 仅代表调用方提供的内容引用；本类不承担查询或生成票务主键的责任。
        movieId = requireText(movieId, "movieId");
        cinemaId = requireText(cinemaId, "cinemaId");
        // 即使不可购候选也要显示来源，避免页面把固定目录当成实时推荐。
        source = requireText(source, "source");
        if (purchaseEligible && (isBlank(showId) || isBlank(price) || startTime == null)) {
            throw new IllegalArgumentException("可购候选必须包含 A 返回的场次、价格和开场时间");
        }
    }

    private static String requireText(String value, String fieldName) {
        // 空标识会使候选无法关联到请求条件，直接在 DTO 边界拒绝。
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(fieldName + " 不能为空");
        }
        return value;
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
