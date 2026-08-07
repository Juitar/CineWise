package com.miaoyu.ticket.recommendation.domain;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.Objects;

/**
 * 一次推荐的用户明确条件。
 *
 * <p>它不携带用户画像、情绪标签或票务事实；场次、票价和余座只能由 A 的公开查询补充。</p>
 */
public record RecommendationConstraints(
        String cityCode,
        LocalDate date,
        int ticketCount,
        String movieId,
        String cinemaId,
        List<String> genres,
        LocalTime timeFrom,
        LocalTime timeTo,
        LocalTime latestEndTime,
        BigDecimal budget,
        List<String> excludedGenres,
        Integer maxDistanceMeters) {

    /** 保留未启用附近推荐时的旧构造器，避免普通推荐调用方被迫携带位置条件。 */
    public RecommendationConstraints(
            String cityCode, LocalDate date, int ticketCount, String movieId, String cinemaId,
            List<String> genres, LocalTime timeFrom, LocalTime timeTo, LocalTime latestEndTime,
            BigDecimal budget, List<String> excludedGenres) {
        this(cityCode, date, ticketCount, movieId, cinemaId, genres, timeFrom, timeTo, latestEndTime,
                budget, excludedGenres, null);
    }

    /** 统一拒绝半个时段和不受控的数量，避免不同调用入口得到不同结果。 */
    public RecommendationConstraints {
        cityCode = requireText(cityCode, "cityCode");
        date = Objects.requireNonNull(date, "date 不能为空");
        if (ticketCount <= 0) {
            throw new IllegalArgumentException("ticketCount 必须大于 0");
        }
        if ((timeFrom == null) != (timeTo == null) || timeFrom != null && !timeFrom.isBefore(timeTo)) {
            throw new IllegalArgumentException("timeFrom 和 timeTo 必须成对且前者早于后者");
        }
        if (budget != null && budget.signum() < 0) {
            throw new IllegalArgumentException("budget 不能为负数");
        }
        if (maxDistanceMeters != null && maxDistanceMeters <= 0) {
            throw new IllegalArgumentException("maxDistanceMeters 必须大于 0");
        }
        // List.copyOf 同时禁止空元素，防止类型过滤在运行期出现空指针。
        genres = List.copyOf(Objects.requireNonNull(genres, "genres 不能为空"));
        excludedGenres = List.copyOf(Objects.requireNonNull(excludedGenres, "excludedGenres 不能为空"));
    }

    private static String requireText(String value, String fieldName) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(fieldName + " 不能为空");
        }
        return value;
    }
}
