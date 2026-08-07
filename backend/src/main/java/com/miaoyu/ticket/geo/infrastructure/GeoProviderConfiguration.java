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
        return new AmapPlaceGeocodingAdapter(properties, externalRestClient);
    }
}
