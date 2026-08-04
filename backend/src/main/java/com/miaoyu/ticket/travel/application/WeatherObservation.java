package com.miaoyu.ticket.travel.application;

import java.time.OffsetDateTime;

/** 天气查询的内部标准结果，任何来源都必须带时效和降级标识。 */
public record WeatherObservation(
        String area, String condition, String risk, String source, OffsetDateTime dataTime,
        OffsetDateTime expiresAt, boolean isExpired, boolean degraded, String fallbackType) {
}
