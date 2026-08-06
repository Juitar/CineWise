package com.miaoyu.ticket.recommendation.domain;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Objects;

/** 一个可展示的推荐方案；票价和场次均必须来自 A 的批量只读查询。 */
public record RecommendationPlan(
        PlanType planType, String movieId, String cinemaId, String showId, BigDecimal price, Instant startTime,
        double score, List<String> reasons, List<RecommendationEvidence> evidence, String source,
        Instant dataAt, Instant expiresAt, boolean purchaseEligible) {

    public enum PlanType { COMPREHENSIVE, LOW_PRICE, EARLY_TIME, NEAREST }

    /** 方案在这里保留完整性，后续评分器不能补造任何票务字段。 */
    public RecommendationPlan {
        planType = Objects.requireNonNull(planType, "planType 不能为空");
        movieId = requireId(movieId, "movieId");
        cinemaId = requireId(cinemaId, "cinemaId");
        showId = requireId(showId, "showId");
        price = Objects.requireNonNull(price, "price 不能为空");
        startTime = Objects.requireNonNull(startTime, "startTime 不能为空");
        reasons = List.copyOf(Objects.requireNonNull(reasons, "reasons 不能为空"));
        evidence = List.copyOf(Objects.requireNonNull(evidence, "evidence 不能为空"));
        source = Objects.requireNonNull(source, "source 不能为空");
        dataAt = Objects.requireNonNull(dataAt, "dataAt 不能为空");
        expiresAt = Objects.requireNonNull(expiresAt, "expiresAt 不能为空");
    }

    private static String requireId(String value, String name) {
        if (value == null || !value.matches("[1-9]\\d*")) {
            throw new IllegalArgumentException(name + " 必须是正十进制业务 ID");
        }
        return value;
    }
}
