package com.miaoyu.ticket.recommendation.domain;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Objects;

/** 一个可展示的推荐方案；票价和场次均必须来自 A 的批量只读查询。 */
public record RecommendationPlan(
        PlanType planType, String movieId, String movieName, String cinemaId, String cinemaName, String showId,
        BigDecimal price, Instant startTime, BigDecimal rating, Integer distanceMeters,
        Integer estimatedTravelMinutes, double score, List<String> reasons, List<RecommendationEvidence> evidence,
        String source, Instant dataAt, Instant expiresAt, boolean purchaseEligible) {

    /** 兼容 D 内部排序器旧夹具；新增展示字段没有来源时按规则留空。 */
    public RecommendationPlan(PlanType planType, String movieId, String cinemaId, String showId, BigDecimal price,
            Instant startTime, double score, List<String> reasons, List<RecommendationEvidence> evidence,
            String source, Instant dataAt, Instant expiresAt, boolean purchaseEligible) {
        this(planType, movieId, null, cinemaId, null, showId, price, startTime, null, null, null, score, reasons,
                evidence, source, dataAt, expiresAt, purchaseEligible);
    }

    public enum PlanType { COMPREHENSIVE, LOW_PRICE, EARLY_TIME, NEAREST }

    /** 方案在这里保留完整性，后续评分器不能补造任何票务字段。 */
    public RecommendationPlan {
        planType = Objects.requireNonNull(planType, "planType 不能为空");
        movieId = requireId(movieId, "movieId");
        if (movieName != null && movieName.isBlank()) {
            throw new IllegalArgumentException("movieName 不能为空字符串");
        }
        cinemaId = requireId(cinemaId, "cinemaId");
        if (cinemaName != null && cinemaName.isBlank()) {
            throw new IllegalArgumentException("cinemaName 不能为空字符串");
        }
        showId = requireId(showId, "showId");
        price = Objects.requireNonNull(price, "price 不能为空");
        startTime = Objects.requireNonNull(startTime, "startTime 不能为空");
        if (rating != null && rating.signum() < 0) {
            throw new IllegalArgumentException("rating 不能为负数");
        }
        if ((distanceMeters != null && distanceMeters < 0)
                || (estimatedTravelMinutes != null && estimatedTravelMinutes < 0)) {
            throw new IllegalArgumentException("距离和预计路程不能为负数");
        }
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
