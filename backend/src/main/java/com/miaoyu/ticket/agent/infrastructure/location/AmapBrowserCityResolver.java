package com.miaoyu.ticket.agent.infrastructure.location;

import com.fasterxml.jackson.databind.JsonNode;
import com.miaoyu.ticket.agent.application.location.BrowserCityResolver;
import java.math.BigDecimal;
import java.util.Optional;
import org.springframework.web.client.RestClient;

/** 高德反向地理编码适配器；失败只返回空结果，绝不记录精确位置或外部异常。 */
final class AmapBrowserCityResolver implements BrowserCityResolver {

    private static final String REVERSE_GEOCODE_ENDPOINT = "/v3/geocode/regeo?"
            + "location={longitude},{latitude}&key={key}";

    private final RestClient restClient;
    private final String key;

    AmapBrowserCityResolver(RestClient restClient, String key) {
        this.restClient = restClient;
        this.key = key;
    }

    @Override
    public Optional<String> resolveCity(BigDecimal longitude, BigDecimal latitude) {
        try {
            JsonNode response = restClient.get()
                    .uri(REVERSE_GEOCODE_ENDPOINT, longitude.toPlainString(), latitude.toPlainString(), key)
                    .retrieve()
                    .body(JsonNode.class);
            if (response == null || !"1".equals(response.path("status").asText())) {
                return Optional.empty();
            }
            return Optional.ofNullable(response.path("regeocode").path("addressComponent").path("city").textValue())
                    .map(String::trim)
                    .filter(value -> !value.isEmpty());
        } catch (RuntimeException exception) {
            return Optional.empty();
        }
    }
}
