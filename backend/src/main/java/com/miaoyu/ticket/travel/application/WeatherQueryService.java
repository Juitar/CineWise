package com.miaoyu.ticket.travel.application;

import com.miaoyu.ticket.common.config.ClockConfiguration;
import java.time.Clock;
import java.time.OffsetDateTime;
import java.util.Optional;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

/**
 * 统一天气来源选择：真实结果、有效缓存、版本化 Demo、明确不可用。
 *
 * <p>天气失败只影响建议内容，不能让支付任务或电子票失败；空结果保留来源时效字段，页面不会把它误认为
 * 实时晴天。</p>
 */
@Service
public class WeatherQueryService {

    private final WeatherProvider realWeatherProvider;
    private final WeatherProvider demoWeatherProvider;
    private final WeatherCache weatherCache;
    private final Clock clock;

    public WeatherQueryService(@Qualifier("realWeatherProvider") WeatherProvider realWeatherProvider,
                               @Qualifier("demoWeatherProvider") WeatherProvider demoWeatherProvider,
                               WeatherCache weatherCache, Clock clock) {
        this.realWeatherProvider = realWeatherProvider;
        this.demoWeatherProvider = demoWeatherProvider;
        this.weatherCache = weatherCache;
        this.clock = clock;
    }

    public WeatherObservation query(String cinemaArea) {
        OffsetDateTime now = OffsetDateTime.ofInstant(clock.instant(), ClockConfiguration.BUSINESS_ZONE_ID);
        return queryRealSafely(cinemaArea, now)
                .map(result -> weatherCache.save(result))
                .or(() -> weatherCache.findValid(cinemaArea, now))
                .or(() -> demoWeatherProvider.query(cinemaArea, now))
                .orElseGet(() -> new WeatherObservation(cinemaArea, null, "天气暂不可用", "UNAVAILABLE", now, now,
                        true, true, "NONE"));
    }

    /** 外部网络异常等同于本次来源不可用，不能跳过缓存和 Demo 回退。 */
    private Optional<WeatherObservation> queryRealSafely(String cinemaArea, OffsetDateTime now) {
        try {
            return realWeatherProvider.query(cinemaArea, now);
        } catch (RuntimeException exception) {
            return Optional.empty();
        }
    }

    /** 缓存端口不保存用户位置，只按影院区域保存短期天气结果。 */
    public interface WeatherCache {
        Optional<WeatherObservation> findValid(String cinemaArea, OffsetDateTime now);
        WeatherObservation save(WeatherObservation observation);
    }
}
