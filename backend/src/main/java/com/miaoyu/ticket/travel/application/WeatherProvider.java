package com.miaoyu.ticket.travel.application;

import java.time.OffsetDateTime;
import java.util.Optional;

/** 外部天气适配端口；没有真实配置时返回空而非编造实时天气。 */
@FunctionalInterface
public interface WeatherProvider {
    Optional<WeatherObservation> query(String cinemaArea, OffsetDateTime requestedAt);
}
