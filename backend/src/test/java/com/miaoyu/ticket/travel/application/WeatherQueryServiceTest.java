package com.miaoyu.ticket.travel.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import com.miaoyu.ticket.content.application.CinemaLocationQueryService;
import com.miaoyu.ticket.geo.domain.LocationGranularity;
import com.miaoyu.ticket.geo.domain.ResolvedGeoPoint;
import java.time.Clock;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

class WeatherQueryServiceTest {

    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-08-04T00:00:00Z"), ZoneOffset.UTC);

    @Test
    void givenRealCacheDemoAndUnavailablePaths_whenQuerying_thenExposeStableMetadata() {
        WeatherQueryService.WeatherCache cache = new InMemoryCache();
        WeatherObservation real = observation("REAL", false, null);
        WeatherQueryService service = new WeatherQueryService((area, time) -> Optional.of(real),
                (area, time) -> Optional.of(observation("DEMO_WEATHER_V1", true, "DEMO")), cache, CLOCK);
        assertThat(service.query("西湖区").source()).isEqualTo("REAL");
        WeatherQueryService cachedService = new WeatherQueryService((area, time) -> Optional.empty(),
                (area, time) -> Optional.of(observation("DEMO_WEATHER_V1", true, "DEMO")), cache, CLOCK);
        assertThat(cachedService.query("西湖区").source()).isEqualTo("REAL");
        WeatherQueryService demoService = new WeatherQueryService((area, time) -> Optional.empty(),
                (area, time) -> Optional.of(observation("DEMO_WEATHER_V1", true, "DEMO")), new InMemoryCache(), CLOCK);
        assertThat(demoService.query("西湖区").fallbackType()).isEqualTo("DEMO");
        WeatherQueryService unavailable = new WeatherQueryService((area, time) -> Optional.empty(),
                (area, time) -> Optional.empty(), new InMemoryCache(), CLOCK);
        assertThat(unavailable.query("西湖区").source()).isEqualTo("UNAVAILABLE");
    }

    @Test
    void givenRealProviderThrows_whenQuerying_thenFallbackToDemo() {
        WeatherQueryService service = new WeatherQueryService(
                (area, time) -> { throw new IllegalStateException("天气网络超时"); },
                (area, time) -> Optional.of(observation("DEMO_WEATHER_V1", true, "DEMO")),
                new InMemoryCache(), CLOCK);

        assertThat(service.query("西湖区").fallbackType()).isEqualTo("DEMO");
    }

    @Test
    void givenCinemaCoordinateAndResolvedAdcode_whenQuerying_thenUseAdcodeWithoutExposingItAsArea() {
        CinemaLocationQueryService cinemas = Mockito.mock(CinemaLocationQueryService.class);
        WeatherAdcodeAdapter adcodes = Mockito.mock(WeatherAdcodeAdapter.class);
        ResolvedGeoPoint point = new ResolvedGeoPoint(
                new java.math.BigDecimal("112.938814"), new java.math.BigDecimal("28.228209"),
                LocationGranularity.ADDRESS);
        when(cinemas.findAreaByCinemaId(4L)).thenReturn(Optional.of("岳麓区"));
        when(cinemas.findByCinemaId(4L)).thenReturn(Optional.of(point));
        when(adcodes.resolve(point)).thenReturn(Optional.of("430104"));
        WeatherQueryService service = new WeatherQueryService(
                (key, time) -> Optional.of(observation("AMAP_WEATHER", false, null)),
                (key, time) -> Optional.empty(), new InMemoryCache(), CLOCK, cinemas, adcodes);

        WeatherObservation result = service.query(4L);

        assertThat(result.area()).isEqualTo("岳麓区");
        org.mockito.Mockito.verify(adcodes).resolve(point);
    }

    private WeatherObservation observation(String source, boolean degraded, String fallback) {
        OffsetDateTime now = OffsetDateTime.ofInstant(CLOCK.instant(), ZoneOffset.UTC);
        return new WeatherObservation(
                "西湖区", "多云", "提前出发", source, now, now.plusMinutes(15), false, degraded, fallback);
    }

    private static final class InMemoryCache implements WeatherQueryService.WeatherCache {
        private WeatherObservation value;
        @Override public Optional<WeatherObservation> findValid(String area, OffsetDateTime now) {
            return Optional.ofNullable(value).filter(item -> item.expiresAt().isAfter(now));
        }
        @Override
        public WeatherObservation save(WeatherObservation observation) {
            value = observation;
            return observation;
        }
    }
}
