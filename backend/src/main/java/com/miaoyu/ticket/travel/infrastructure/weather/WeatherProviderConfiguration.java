package com.miaoyu.ticket.travel.infrastructure.weather;

import com.miaoyu.ticket.travel.application.WeatherProvider;
import com.miaoyu.ticket.travel.application.WeatherQueryService.WeatherCache;
import java.util.Optional;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** 未配置真实天气时注册空实现，强制走缓存或 Demo，而非伪造网络成功。 */
@Configuration(proxyBeanMethods = false)
public class WeatherProviderConfiguration {
    @Bean WeatherProvider realWeatherProvider() { return (area, time) -> Optional.empty(); }
    @Bean WeatherProvider demoWeatherProvider() { return new DemoWeatherProvider(); }
    @Bean WeatherCache weatherCache() { return new InMemoryWeatherCache(); }
}
