package com.miaoyu.ticket.travel.infrastructure.route;

import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.web.client.RestClient;

/** 仅将本次起点、影院终点和 Key 发送给高德，不记录请求参数。 */
final class RestClientAmapRouteClient implements AmapRouteClient {
    private static final String DRIVING_ENDPOINT = "/v3/direction/driving?origin={origin}&destination={destination}"
            + "&strategy=0&key={key}";
    private static final String WALKING_ENDPOINT = "/v3/direction/walking?origin={origin}&destination={destination}"
            + "&key={key}";
    private final RestClient restClient;

    RestClientAmapRouteClient(RestClient restClient) {
        this.restClient = restClient;
    }

    @Override
    public JsonNode queryDrivingRoute(String origin, String destination, String key) {
        return restClient.get().uri(DRIVING_ENDPOINT, origin, destination, key).retrieve().body(JsonNode.class);
    }

    @Override
    public JsonNode queryWalkingRoute(String origin, String destination, String key) {
        return restClient.get().uri(WALKING_ENDPOINT, origin, destination, key).retrieve().body(JsonNode.class);
    }
}
