package com.miaoyu.ticket.travel.application;

import com.miaoyu.ticket.common.config.ClockConfiguration;
import com.miaoyu.ticket.content.application.CinemaLocationQueryService;
import com.miaoyu.ticket.geo.domain.ResolvedGeoPoint;
import java.time.Clock;
import java.time.OffsetDateTime;
import java.util.Optional;
import org.springframework.beans.factory.annotation.Autowired;
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
    private final CinemaLocationQueryService cinemaLocationQueryService;
    private final WeatherAdcodeAdapter weatherAdcodeAdapter;

    @Autowired
    public WeatherQueryService(@Qualifier("realWeatherProvider") WeatherProvider realWeatherProvider,
                               @Qualifier("demoWeatherProvider") WeatherProvider demoWeatherProvider,
                               WeatherCache weatherCache, Clock clock,
                               CinemaLocationQueryService cinemaLocationQueryService,
                               WeatherAdcodeAdapter weatherAdcodeAdapter) {
        this.realWeatherProvider = realWeatherProvider;
        this.demoWeatherProvider = demoWeatherProvider;
        this.weatherCache = weatherCache;
        this.clock = clock;
        this.cinemaLocationQueryService = cinemaLocationQueryService;
        this.weatherAdcodeAdapter = weatherAdcodeAdapter;
    }

    /** 保留原有单元测试构造入口；它仅支持区域查询，不能用于 cinemaId Tool。 */
    public WeatherQueryService(WeatherProvider realWeatherProvider, WeatherProvider demoWeatherProvider,
                               WeatherCache weatherCache, Clock clock) {
        this.realWeatherProvider = realWeatherProvider;
        this.demoWeatherProvider = demoWeatherProvider;
        this.weatherCache = weatherCache;
        this.clock = clock;
        this.cinemaLocationQueryService = null;
        this.weatherAdcodeAdapter = null;
    }

    /**
     * Tool 和页面统一只传影院标识，拒绝模型传入的区域、地址或用户位置。
     *
     * <p>当前区域映射仅作为逆地理 Adapter 未启用时的显式回退；正常路径将在 Provider 层以影院
     * 数值坐标反查 adcode。没有已登记影院区域时直接返回不可用，不猜测行政区。</p>
     */
    public WeatherObservation query(long cinemaId) {
        if (cinemaLocationQueryService == null) {
            throw new IllegalStateException("影院位置查询服务未配置");
        }
        String area = cinemaLocationQueryService.findAreaByCinemaId(cinemaId).orElse(null);
        if (area == null) {
            OffsetDateTime now = OffsetDateTime.ofInstant(clock.instant(), ClockConfiguration.BUSINESS_ZONE_ID);
            return new WeatherObservation(null, null, "天气暂不可用", "UNAVAILABLE", now, now,
                    true, true, "NONE");
        }
        ResolvedGeoPoint location = cinemaLocationQueryService.findByCinemaId(cinemaId).orElse(null);
        if (location != null && weatherAdcodeAdapter != null) {
            Optional<String> adcode = weatherAdcodeAdapter.resolve(location);
            if (adcode.isPresent()) {
                // 缓存键使用 adcode；返回前恢复展示区域，避免对外泄露 Provider 内部标识。
                WeatherObservation observation = query(adcode.orElseThrow());
                return new WeatherObservation(
                        area, observation.condition(), observation.risk(), observation.source(),
                        observation.dataTime(), observation.expiresAt(), observation.isExpired(),
                        observation.degraded(),
                        observation.fallbackType());
            }
        }
        WeatherObservation fallback = query(area);
        return new WeatherObservation(
                area, fallback.condition(), fallback.risk(), fallback.source(), fallback.dataTime(),
                fallback.expiresAt(), fallback.isExpired(), true, "AREA_ADCODE_FALLBACK");
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
