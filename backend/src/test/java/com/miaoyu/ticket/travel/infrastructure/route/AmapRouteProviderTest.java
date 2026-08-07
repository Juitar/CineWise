package com.miaoyu.ticket.travel.infrastructure.route;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
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
            assertThat(destination).isEqualTo("西湖区");
            assertThat(key).isEqualTo("test-key");
            return json("{\"status\":\"1\",\"route\":{\"paths\":[{\"duration\":\"1250\"}]}}");
        });

        var result = provider.plan("120.1,30.2", "西湖区", "DRIVING", REQUESTED_AT);

        assertThat(result).isPresent();
        assertThat(result.orElseThrow().source()).isEqualTo("AMAP_ROUTE");
        assertThat(result.orElseThrow().durationMinutes()).isEqualTo(21);
        assertThat(result.orElseThrow().degraded()).isFalse();
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

        assertThat(disabled.plan("120.1,30.2", "西湖区", "DRIVING", REQUESTED_AT)).isEmpty();
        assertThat(missingKey.plan("120.1,30.2", "西湖区", "DRIVING", REQUESTED_AT)).isEmpty();
    }

    @Test
    void givenTimeoutFailureOrIncompleteResponse_whenPlanning_thenReturnEmptyForDemoFallback() {
        AmapRouteProvider timeout = provider((origin, destination, key) -> {
            throw new IllegalStateException("timeout");
        });
        AmapRouteProvider rejected = provider((origin, destination, key) -> json("{\"status\":\"0\"}"));
        AmapRouteProvider incomplete = provider((origin, destination, key) ->
                json("{\"status\":\"1\",\"route\":{\"paths\":[{}]}}"));

        assertThat(timeout.plan("120.1,30.2", "西湖区", "DRIVING", REQUESTED_AT)).isEmpty();
        assertThat(rejected.plan("120.1,30.2", "西湖区", "DRIVING", REQUESTED_AT)).isEmpty();
        assertThat(incomplete.plan("120.1,30.2", "西湖区", "DRIVING", REQUESTED_AT)).isEmpty();
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
}
