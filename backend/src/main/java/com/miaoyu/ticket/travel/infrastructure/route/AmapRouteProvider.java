package com.miaoyu.ticket.travel.infrastructure.route;

import com.fasterxml.jackson.databind.JsonNode;
import com.miaoyu.ticket.travel.application.BasicRouteProvider;
import com.miaoyu.ticket.travel.application.BasicRouteResult;
import java.time.OffsetDateTime;
import java.util.Optional;

/** 高德真实路线适配器，只输出时长和出发时间等安全摘要。 */
final class AmapRouteProvider implements BasicRouteProvider {
    static final String SOURCE = "AMAP_ROUTE";
    private final AmapRouteProperties properties;
    private final AmapRouteClient client;

    AmapRouteProvider(AmapRouteProperties properties, AmapRouteClient client) {
        this.properties = properties;
        this.client = client;
    }

    @Override
    public Optional<BasicRouteResult> plan(
            String originValue, String cinemaArea, String travelMode, OffsetDateTime requestedAt) {
        if (!properties.enabled() || properties.key().isBlank() || blank(originValue) || blank(cinemaArea)) {
            return Optional.empty();
        }
        try {
            JsonNode response = client.queryDrivingRoute(originValue, cinemaArea, properties.key());
            if (!"1".equals(response.path("status").asText())) {
                return Optional.empty();
            }
            JsonNode path = response.path("route").path("paths").path(0);
            long seconds = path.path("duration").asLong(-1);
            if (seconds <= 0 || seconds > Integer.MAX_VALUE * 60L) {
                return Optional.empty();
            }
            int minutes = Math.max(1, (int) Math.ceil(seconds / 60.0));
            return Optional.of(new BasicRouteResult(
                    "AMAP", travelMode, minutes, requestedAt.plusMinutes(minutes), SOURCE, requestedAt,
                    requestedAt.plus(properties.resultTtl()), false, false, null));
        } catch (RuntimeException exception) {
            // 超时、网络异常和非 2xx 都交给降级 Provider；异常中不得拼入起点或 Key。
            return Optional.empty();
        }
    }

    private boolean blank(String value) {
        return value == null || value.isBlank();
    }
}
