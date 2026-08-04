package com.miaoyu.ticket.travel.infrastructure.weather;

import com.miaoyu.ticket.travel.application.WeatherObservation;
import com.miaoyu.ticket.travel.application.WeatherProvider;
import java.time.OffsetDateTime;
import java.util.Optional;

/** 固定 Demo 天气只用于离线演示，明确标记为降级数据。 */
public class DemoWeatherProvider implements WeatherProvider {

    @Override
    public Optional<WeatherObservation> query(String cinemaArea, OffsetDateTime requestedAt) {
        return Optional.of(new WeatherObservation(cinemaArea, "多云", "请预留20分钟入场缓冲", "DEMO_WEATHER_V1",
                requestedAt, requestedAt.plusMinutes(15), false, true, "DEMO"));
    }
}
