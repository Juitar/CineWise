package com.miaoyu.ticket.travel.infrastructure.weather;

import com.miaoyu.ticket.travel.application.WeatherObservation;
import com.miaoyu.ticket.travel.application.WeatherQueryService.WeatherCache;
import java.time.OffsetDateTime;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/** 本期 Demo 缓存；真实 Redis 缓存接入时保持同一时效筛选规则。 */
public class InMemoryWeatherCache implements WeatherCache {
    private final ConcurrentHashMap<String, WeatherObservation> entries = new ConcurrentHashMap<>();
    @Override public Optional<WeatherObservation> findValid(String area, OffsetDateTime now) {
        return Optional.ofNullable(entries.get(area)).filter(item -> item.expiresAt().isAfter(now));
    }
    @Override
    public WeatherObservation save(WeatherObservation observation) {
        entries.put(observation.area(), observation);
        return observation;
    }
}
