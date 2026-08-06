package com.miaoyu.ticket.recommendation.domain;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Objects;

/** A 的场次事实与内容类型、评分合并后的只读候选，供后续过滤和评分使用。 */
public record RankedRecommendationCandidate(
        String movieId, String cinemaId, String showId, BigDecimal price, Instant startTime, Instant endTime,
        List<String> genres, BigDecimal rating, int availableSeatCount, String source, Instant dataAt,
        Instant expiresAt) {

    /** 兼容旧测试夹具；真实票务适配器必须使用含余座的完整构造方式。 */
    public RankedRecommendationCandidate(
            String movieId, String cinemaId, String showId, BigDecimal price, Instant startTime, Instant endTime,
            List<String> genres, BigDecimal rating, String source, Instant dataAt, Instant expiresAt) {
        this(movieId, cinemaId, showId, price, startTime, endTime, genres, rating, Integer.MAX_VALUE, source,
                dataAt, expiresAt);
    }

    /** 此处只检查候选是否可被后续规则消费，不修改 A 返回的票务事实。 */
    public RankedRecommendationCandidate {
        movieId = requireId(movieId, "movieId");
        cinemaId = requireId(cinemaId, "cinemaId");
        showId = requireId(showId, "showId");
        price = Objects.requireNonNull(price, "price 不能为空");
        startTime = Objects.requireNonNull(startTime, "startTime 不能为空");
        endTime = Objects.requireNonNull(endTime, "endTime 不能为空");
        genres = List.copyOf(Objects.requireNonNull(genres, "genres 不能为空"));
        if (availableSeatCount < 0) {
            throw new IllegalArgumentException("availableSeatCount 不能为负数");
        }
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
