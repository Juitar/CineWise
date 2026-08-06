package com.miaoyu.ticket.travel.infrastructure.weather;

import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.web.client.RestClient;

/**
 * 使用公共 HTTP 超时配置访问高德天气接口。
 *
 * <p>每次调用只发送影院区域对应的行政区码；不发送用户经纬度、地址、路线或身份信息。异常交给 Provider
 * 转换为空结果，使上层继续执行缓存和 Demo 回退。</p>
 *
 * <p>高德 key 仅作为请求参数使用，不能拼接进业务异常或日志消息。</p>
 */
final class RestClientAmapWeatherClient implements AmapWeatherClient {
    private static final String WEATHER_ENDPOINT = "https://restapi.amap.com/v3/weather/weatherInfo"
            + "?city={city}&key={key}&extensions=base";
    private final RestClient restClient;

    RestClientAmapWeatherClient(RestClient restClient) {
        this.restClient = restClient;
    }

    /** 高德返回非 2xx 时 RestClient 抛异常，不能将错误页面误当作天气 JSON。 */
    @Override
    public JsonNode query(String cityAdcode, String key) {
        return restClient.get().uri(WEATHER_ENDPOINT, cityAdcode, key).retrieve().body(JsonNode.class);
    }
}
