package com.miaoyu.ticket.travel.infrastructure.route;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.miaoyu.ticket.geo.domain.LocationGranularity;
import com.miaoyu.ticket.geo.domain.ResolvedGeoPoint;
import java.math.BigDecimal;
import java.time.Duration;
import java.time.OffsetDateTime;
import org.junit.jupiter.api.Test;

class AmapRouteProviderTest {

    private static final OffsetDateTime REQUESTED_AT = OffsetDateTime
            .parse("2026-08-07T10:00:00+08:00");
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void givenValidAmapResponse_whenPlanning_thenReturnRealRouteSummary() {
        AmapRouteProvider provider = provider((origin, destination, key) -> {
            assertThat(origin).isEqualTo("120.1,30.2");
            assertThat(destination).isEqualTo("120.2,30.3");
            assertThat(key).isEqualTo("test-key");
            return json("{\"status\":\"1\",\"route\":{\"paths\":[{\"duration\":\"1250\"}]}}");
        });

        var result = provider.plan(point("120.1", "30.2"), point("120.2", "30.3"), "DRIVING", REQUESTED_AT);

        assertThat(result).isPresent();
        assertThat(result.orElseThrow().source()).isEqualTo("AMAP_ROUTE");
        assertThat(result.orElseThrow().durationMinutes()).isEqualTo(21);
        assertThat(result.orElseThrow().degraded()).isFalse();
    }

    @Test
    void givenWalkingMode_whenPlanning_thenUseWalkingClientAndNormalizeMode() {
        AmapRouteProvider provider = new AmapRouteProvider(
                new AmapRouteProperties(true, "test-key", Duration.ofSeconds(2), Duration.ofSeconds(5),
                        Duration.ofMinutes(15)), new AmapRouteClient() {
                            @Override
                            public JsonNode queryDrivingRoute(String origin, String destination, String key) {
                                throw new AssertionError("步行不应请求驾车接口");
                            }

                            @Override
                            public JsonNode queryWalkingRoute(String origin, String destination, String key) {
                                assertThat(origin).isEqualTo("120.1,30.2");
                                assertThat(destination).isEqualTo("120.2,30.3");
                                return json("{\"status\":\"1\",\"route\":{\"paths\":[{\"duration\":\"600\"}]}}");
                            }
                        });

        var result = provider.plan(point("120.1", "30.2"), point("120.2", "30.3"), "walking", REQUESTED_AT);

        assertThat(result).isPresent();
        assertThat(result.orElseThrow().travelMode()).isEqualTo("WALKING");
        assertThat(result.orElseThrow().durationMinutes()).isEqualTo(10);
    }

    @Test
    void givenUnsupportedMode_whenPlanning_thenDoNotCallAmap() {
        AmapRouteProvider provider = provider((origin, destination, key) -> {
            throw new AssertionError("不支持的模式不应请求高德");
        });

        assertThat(provider.plan(point("120.1", "30.2"), point("120.2", "30.3"), "TRANSIT", REQUESTED_AT))
                .isEmpty();
    }

    @Test
    void givenDisabledOrMissingKey_whenPlanning_thenDoNotCallAmap() {
        AmapRouteProvider disabled = new AmapRouteProvider(
                new AmapRouteProperties(false, "test-key", Duration.ofSeconds(2), Duration.ofSeconds(5),
                        Duration.ofMinutes(15)),
                (origin, destination, key) -> { throw new AssertionError("不应请求高德"); });
        AmapRouteProvider missingKey = new AmapRouteProvider(
                new AmapRouteProperties(true, "", Duration.ofSeconds(2), Duration.ofSeconds(5),
                        Duration.ofMinutes(15)),
                (origin, destination, key) -> { throw new AssertionError("不应请求高德"); });

        assertThat(disabled.plan(point("120.1", "30.2"), point("120.2", "30.3"), "DRIVING", REQUESTED_AT)).isEmpty();
        assertThat(missingKey.plan(point("120.1", "30.2"), point("120.2", "30.3"), "DRIVING", REQUESTED_AT)).isEmpty();
    }

    @Test
    void givenTimeoutFailureOrIncompleteResponse_whenPlanning_thenReturnEmptyForDemoFallback() {
        AmapRouteProvider timeout = provider((origin, destination, key) -> {
            throw new IllegalStateException("timeout");
        });
        AmapRouteProvider rejected = provider((origin, destination, key) -> json("{\"status\":\"0\"}"));
        AmapRouteProvider incomplete = provider((origin, destination, key) ->
                json("{\"status\":\"1\",\"route\":{\"paths\":[{}]}}"));

        assertThat(timeout.plan(point("120.1", "30.2"), point("120.2", "30.3"), "DRIVING", REQUESTED_AT)).isEmpty();
        assertThat(rejected.plan(point("120.1", "30.2"), point("120.2", "30.3"), "DRIVING", REQUESTED_AT)).isEmpty();
        assertThat(incomplete.plan(point("120.1", "30.2"), point("120.2", "30.3"), "DRIVING", REQUESTED_AT)).isEmpty();
    }

    private AmapRouteProvider provider(AmapRouteClient client) {
        return new AmapRouteProvider(
                new AmapRouteProperties(true, "test-key", Duration.ofSeconds(2), Duration.ofSeconds(5),
                        Duration.ofMinutes(15)), client);
    }

    private JsonNode json(String value) {
        try {
            return objectMapper.readTree(value);
        } catch (Exception exception) {
            throw new AssertionError(exception);
        }
    }

    /** 用数值坐标驱动 Provider；字符串只允许在适配器内部出现。 */
    private ResolvedGeoPoint point(String longitude, String latitude) {
        return new ResolvedGeoPoint(new BigDecimal(longitude), new BigDecimal(latitude), LocationGranularity.ADDRESS);
    }
}
