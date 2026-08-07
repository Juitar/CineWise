package com.miaoyu.ticket.geo.infrastructure.amap;

import com.fasterxml.jackson.databind.JsonNode;
import com.miaoyu.ticket.geo.application.PlaceGeocodingPort;
import com.miaoyu.ticket.geo.domain.LocationGranularity;
import com.miaoyu.ticket.travel.infrastructure.weather.AmapWeatherProperties;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import org.springframework.web.client.RestClient;

/** 高德地理编码 Adapter，只在基础设施层解析外部 JSON。 */
public final class AmapPlaceGeocodingAdapter implements PlaceGeocodingPort {
    private static final String ENDPOINT = "/v3/geocode/geo?address={address}&key={key}";
    private final AmapWeatherProperties properties;
    private final RestClient restClient;

    public AmapPlaceGeocodingAdapter(AmapWeatherProperties properties, RestClient restClient) {
        this.properties = properties;
        this.restClient = restClient;
    }

    @Override
    public List<Candidate> geocode(String placeText) {
        if (!properties.enabled() || properties.key().isBlank() || placeText == null || placeText.isBlank()) {
            return List.of();
        }
        try {
            JsonNode response = restClient.get().uri(ENDPOINT, placeText, properties.key())
                    .retrieve().body(JsonNode.class);
            if (response == null || !"1".equals(response.path("status").asText())
                    || !"10000".equals(response.path("infocode").asText())
                    || !response.path("geocodes").isArray()) {
                return List.of();
            }
            List<Candidate> candidates = new ArrayList<>();
            for (JsonNode geocode : response.path("geocodes")) {
                Candidate candidate = toCandidate(geocode);
                if (candidate != null) {
                    candidates.add(candidate);
                }
            }
            return List.copyOf(candidates);
        } catch (RuntimeException exception) {
            return List.of();
        }
    }

    private Candidate toCandidate(JsonNode geocode) {
        String location = geocode.path("location").asText("");
        String[] coordinates = location.split(",", -1);
        if (coordinates.length != 2) {
            return null;
        }
        try {
            return new Candidate(new BigDecimal(coordinates[0]), new BigDecimal(coordinates[1]),
                    granularity(geocode.path("level").asText("")));
        } catch (RuntimeException exception) {
            return null;
        }
    }

    private LocationGranularity granularity(String level) {
        // “住宅区”“商圈”等地点级别虽包含“区”字，但可代表一个 POI，不能误降为行政区。
        if (level.contains("住宅") || level.contains("商圈") || level.contains("兴趣点")) {
            return LocationGranularity.POI;
        }
        if (level.contains("市")) {
            return LocationGranularity.CITY;
        }
        if (level.contains("区") || level.contains("县")) {
            return LocationGranularity.DISTRICT;
        }
        if (level.contains("道路") || level.contains("门址")) {
            return LocationGranularity.ADDRESS;
        }
        return LocationGranularity.POI;
    }
}
