package com.miaoyu.ticket.geo.infrastructure;

import com.miaoyu.ticket.geo.application.PlaceGeocodingPort;
import com.miaoyu.ticket.geo.infrastructure.amap.AmapPlaceGeocodingAdapter;
import com.miaoyu.ticket.travel.infrastructure.weather.AmapWeatherProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** 未配置 Key 时 Adapter 返回无候选，不能把 Demo 城市伪装成用户地点。 */
@Configuration(proxyBeanMethods = false)
public class GeoProviderConfiguration {

    @Bean
    PlaceGeocodingPort placeGeocodingPort(
            AmapWeatherProperties properties, org.springframework.web.client.RestClient externalRestClient) {
        // 外部公共客户端没有 baseUrl；地点编码使用相对路径，必须在这里固定到高德域名。
        // 保留 externalRestClient 已配置的超时和无重试策略，不能改用默认 RestClient。
        return new AmapPlaceGeocodingAdapter(
                properties,
                externalRestClient.mutate().baseUrl("https://restapi.amap.com").build());
    }
}
