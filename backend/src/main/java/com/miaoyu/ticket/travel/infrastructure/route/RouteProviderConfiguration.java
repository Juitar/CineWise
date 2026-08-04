package com.miaoyu.ticket.travel.infrastructure.route;

import com.miaoyu.ticket.travel.application.BasicRouteProvider;
import java.util.Optional;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** 未配置真实地图服务时显式返回不可用，避免 Demo 把虚构路线展示为真实导航。 */
@Configuration(proxyBeanMethods = false)
public class RouteProviderConfiguration {

    @Bean
    BasicRouteProvider basicRouteProvider() {
        return (origin, cinemaArea, travelMode, requestedAt) -> Optional.empty();
    }
}
