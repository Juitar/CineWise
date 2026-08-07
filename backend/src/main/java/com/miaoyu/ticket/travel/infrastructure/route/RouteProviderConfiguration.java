package com.miaoyu.ticket.travel.infrastructure.route;

import com.miaoyu.ticket.travel.application.BasicRouteProvider;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

/** 未配置真实地图服务时显式返回不可用，避免 Demo 把虚构路线展示为真实导航。 */
@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(AmapRouteProperties.class)
public class RouteProviderConfiguration {

    @Bean
    BasicRouteProvider basicRouteProvider(AmapRouteProperties properties) {
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(properties.connectTimeout());
        requestFactory.setReadTimeout(properties.readTimeout());
        AmapRouteProvider realProvider = new AmapRouteProvider(
                properties, new RestClientAmapRouteClient(RestClient.builder()
                        .baseUrl("https://restapi.amap.com").requestFactory(requestFactory).build()));
        DemoRouteProvider demoProvider = new DemoRouteProvider();
        return (origin, cinemaArea, travelMode, requestedAt) -> realProvider
                .plan(origin, cinemaArea, travelMode, requestedAt)
                .or(() -> demoProvider.plan(origin, cinemaArea, travelMode, requestedAt));
    }
}
