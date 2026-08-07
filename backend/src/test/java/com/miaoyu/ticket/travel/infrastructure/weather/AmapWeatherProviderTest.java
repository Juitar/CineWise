package com.miaoyu.ticket.travel.infrastructure.weather;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

class AmapWeatherProviderTest {

    private static final OffsetDateTime REQUESTED_AT = OffsetDateTime.parse("2026-08-06T15:00:00+08:00");
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void givenAmapLiveWeather_whenQueryingMappedCinemaArea_thenReturnRealObservationAndAdvice() {
        AtomicInteger calls = new AtomicInteger();
        AmapWeatherProvider provider = provider((adcode, key) -> {
            calls.incrementAndGet();
            assertThat(adcode).isEqualTo("330106");
            assertThat(key).isEqualTo("test-key");
            return responseUnchecked("{\"status\":\"1\",\"lives\":[{\"weather\":\"小雨\",\"temperature\":\"18\","
                    + "\"reporttime\":\"2026-08-06 14:00:00\"}]}");
        });

        var result = provider.query("西湖区", REQUESTED_AT);

        assertThat(result).isPresent();
        assertThat(result.orElseThrow().source()).isEqualTo(AmapWeatherProvider.SOURCE);
        assertThat(result.orElseThrow().condition()).isEqualTo("小雨（18℃）");
        assertThat(result.orElseThrow().risk()).contains("雨具");
        assertThat(result.orElseThrow().dataTime()).isEqualTo(OffsetDateTime.parse("2026-08-06T14:00:00+08:00"));
        assertThat(result.orElseThrow().expiresAt()).isEqualTo(REQUESTED_AT.plusMinutes(15));
        assertThat(result.orElseThrow().degraded()).isFalse();
        assertThat(calls).hasValue(1);
    }

    @Test
    void givenMissingKeyOrAreaAdcode_whenQuerying_thenSkipAmapAndAllowFallback() {
        AtomicInteger calls = new AtomicInteger();
        AmapWeatherProvider missingKey = new AmapWeatherProvider(
                new AmapWeatherProperties(true, "", Duration.ofMinutes(15), Map.of("西湖区", "330106")),
                (adcode, key) -> {
                    calls.incrementAndGet();
                    return responseUnchecked("{}");
                });
        AmapWeatherProvider missingArea = provider((adcode, key) -> {
            calls.incrementAndGet();
            return responseUnchecked("{}");
        });

        assertThat(missingKey.query("西湖区", REQUESTED_AT)).isEmpty();
        assertThat(missingArea.query("未知区域", REQUESTED_AT)).isEmpty();
        assertThat(calls).hasValue(0);
    }

    @Test
    void givenAmapFailurePayloadOrClientError_whenQuerying_thenReturnEmptyForDemoFallback() {
        AmapWeatherProvider rejected = provider((adcode, key) -> responseUnchecked("{\"status\":\"0\"}"));
        AmapWeatherProvider failed = provider((adcode, key) -> { throw new IllegalStateException("timeout"); });

        assertThat(rejected.query("西湖区", REQUESTED_AT)).isEmpty();
        assertThat(failed.query("西湖区", REQUESTED_AT)).isEmpty();
    }

    @Test
    void givenAmapLiveWeatherMissingRequiredFields_whenQuerying_thenReturnEmptyForFallback() {
        AmapWeatherProvider missingTemperature = provider((adcode, key) -> responseUnchecked(
                "{\"status\":\"1\",\"lives\":[{\"weather\":\"小雨\",\"reporttime\":\"2026-08-06 14:00:00\"}]}"));
        AmapWeatherProvider missingReportTime = provider((adcode, key) -> responseUnchecked(
                "{\"status\":\"1\",\"lives\":[{\"weather\":\"小雨\",\"temperature\":\"18\"}]}"));
        AmapWeatherProvider invalidReportTime = provider((adcode, key) -> responseUnchecked(
                "{\"status\":\"1\",\"lives\":[{\"weather\":\"小雨\",\"temperature\":\"18\","
                        + "\"reporttime\":\"invalid\"}]}"));

        assertThat(missingTemperature.query("西湖区", REQUESTED_AT)).isEmpty();
        assertThat(missingReportTime.query("西湖区", REQUESTED_AT)).isEmpty();
        assertThat(invalidReportTime.query("西湖区", REQUESTED_AT)).isEmpty();
    }

    @Test
    void givenCityMapping_whenAreaMappingMissing_thenUseRegisteredCityCode() {
        AmapWeatherProperties properties = new AmapWeatherProperties(
                true, "test-key", Duration.ofMinutes(15), Map.of(), Map.of("杭州市", "330100"));
        AmapWeatherProvider provider = new AmapWeatherProvider(properties, (adcode, key) -> {
            assertThat(adcode).isEqualTo("330100");
            return responseUnchecked("{\"status\":\"1\",\"lives\":[{\"weather\":\"晴\","
                    + "\"temperature\":\"25\",\"reporttime\":\"2026-08-06 14:00:00\"}]}");
        });

        assertThat(provider.query("杭州市", REQUESTED_AT)).isPresent();
    }

    @Test
    void givenAreaAndCityMappings_whenQueryingArea_thenAreaMappingWins() {
        AmapWeatherProperties properties = new AmapWeatherProperties(
                true, "test-key", Duration.ofMinutes(15), Map.of("西湖区", "330106"), Map.of("西湖区", "330100"));
        AmapWeatherProvider provider = new AmapWeatherProvider(properties, (adcode, key) -> {
            assertThat(adcode).isEqualTo("330106");
            return responseUnchecked("{\"status\":\"1\",\"lives\":[{\"weather\":\"晴\","
                    + "\"temperature\":\"25\",\"reporttime\":\"2026-08-06 14:00:00\"}]}");
        });

        assertThat(provider.query("西湖区", REQUESTED_AT)).isPresent();
    }

    private AmapWeatherProvider provider(AmapWeatherClient client) {
        return new AmapWeatherProvider(
                new AmapWeatherProperties(true, "test-key", Duration.ofMinutes(15), Map.of("西湖区", "330106")),
                client);
    }

    private JsonNode response(String value) throws Exception {
        return objectMapper.readTree(value);
    }

    private JsonNode responseUnchecked(String value) {
        try {
            return response(value);
        } catch (Exception exception) {
            throw new AssertionError(exception);
        }
    }
}
