package com.miaoyu.ticket.travel.infrastructure.weather;

import com.fasterxml.jackson.databind.JsonNode;
import com.miaoyu.ticket.geo.domain.ResolvedGeoPoint;
import com.miaoyu.ticket.travel.application.WeatherAdcodeAdapter;
import java.util.Optional;
import org.springframework.web.client.RestClient;

/**
 * 高德逆地理编码适配器。
 *
 * <p>只接收影院的静态坐标，响应中只保留六位 adcode；用户位置、地址文本和 Key 不进入日志、缓存
 * 或业务 DTO。调用失败由上层使用已登记区域映射回退。</p>
 */
public final class AmapWeatherAdcodeAdapter implements WeatherAdcodeAdapter {
    private static final String ENDPOINT = "/v3/geocode/regeo?location={location}&key={key}&extensions=base";
    private final AmapWeatherProperties properties;
    private final RestClient restClient;

    public AmapWeatherAdcodeAdapter(AmapWeatherProperties properties, RestClient restClient) {
        this.properties = properties;
        this.restClient = restClient;
    }

    @Override
    public Optional<String> resolve(ResolvedGeoPoint cinemaLocation) {
        if (!properties.enabled() || properties.key().isBlank() || cinemaLocation == null) {
            return Optional.empty();
        }
        try {
            String location = cinemaLocation.longitude().toPlainString()
                    + "," + cinemaLocation.latitude().toPlainString();
            JsonNode response = restClient.get().uri(ENDPOINT, location, properties.key())
                    .retrieve().body(JsonNode.class);
            String adcode = response == null ? "" : response.path("regeocode")
                    .path("addressComponent").path("adcode").asText();
            return adcode.matches("[1-9][0-9]{5}") ? Optional.of(adcode) : Optional.empty();
        } catch (RuntimeException exception) {
            return Optional.empty();
        }
    }
}
