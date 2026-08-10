package com.miaoyu.ticket.agent.infrastructure.location;

import com.miaoyu.ticket.agent.application.location.BrowserCityResolver;
import com.miaoyu.ticket.travel.infrastructure.route.AmapRouteProperties;
import java.util.Optional;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

/** 复用仅存在于服务端环境变量的高德 REST Key，不向浏览器发送该 Key。 */
@Configuration(proxyBeanMethods = false)
public class AmapBrowserCityResolverConfiguration {

    @Bean
    BrowserCityResolver browserCityResolver(AmapRouteProperties properties) {
        if (!properties.enabled() || properties.key().isBlank()) {
            return (longitude, latitude) -> Optional.empty();
        }
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(properties.connectTimeout());
        requestFactory.setReadTimeout(properties.readTimeout());
        return new AmapBrowserCityResolver(RestClient.builder()
                .baseUrl("https://restapi.amap.com")
                .requestFactory(requestFactory)
                .build(), properties.key());
    }
}
