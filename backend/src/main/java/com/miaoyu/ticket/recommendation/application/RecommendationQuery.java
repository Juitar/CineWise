package com.miaoyu.ticket.recommendation.application;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.Objects;

/**
 * 推荐应用服务的最小查询条件。
 *
 * <p>只接收已校验的影片、影院和时间条件，故意不包含 showId、价格、座位、库存或 userId；这些数据
 * 必须等待 A 的公开场次查询返回。</p>
 */
public record RecommendationQuery(
        String movieId, String cinemaId, LocalDate date, LocalTime timeFrom, LocalTime timeTo) {

    /** 在进入 Application Service 前统一校验时段边界，避免不同调用方得到不同结果。 */
    public RecommendationQuery {
        // 使用字符串 ID 避免 JavaScript 精度损失，并拒绝负数、零和非数字输入。
        movieId = requireBusinessId(movieId, "movieId");
        cinemaId = requireBusinessId(cinemaId, "cinemaId");
        // 日期是 A 公开场次查询的必要条件，缺失时不能退化为任意日期。
        date = Objects.requireNonNull(date, "date 不能为空");
        // 时段必须成对传递，防止半个范围被不同调用方解释成不同含义。
        if ((timeFrom == null) != (timeTo == null)) {
            throw new IllegalArgumentException("timeFrom 和 timeTo 必须同时为空或同时传入");
        }
        if (timeFrom != null && !timeFrom.isBefore(timeTo)) {
            throw new IllegalArgumentException("timeFrom 必须早于 timeTo");
        }
    }

    private static String requireBusinessId(String value, String fieldName) {
        // 业务 ID 对外固定为十进制字符串，不能把空白或带符号的值传给 A。
        if (value == null || !value.matches("[1-9]\\d*")) {
            throw new IllegalArgumentException(fieldName + " 必须是正十进制业务 ID");
        }
        return value;
    }
}
