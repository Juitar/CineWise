package com.miaoyu.ticket.travel.infrastructure.weather;

import com.miaoyu.ticket.travel.application.WeatherProvider;
import com.miaoyu.ticket.travel.application.WeatherQueryService.WeatherCache;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestClient;

/**
 * 注册真实高德天气、Demo 和缓存实现。
 *
 * <p>真实 Provider 即使注册也会在缺 key 或关闭开关时返回空；这一层不以空实现伪装高德成功，天气服务会
 * 继续执行缓存和 Demo 回退。</p>
 */
@Configuration(proxyBeanMethods = false)
public class WeatherProviderConfiguration {
    /** 复用项目统一 HTTP 超时设置，不在天气模块另建无法统一管控的客户端。 */
    @Bean WeatherProvider realWeatherProvider(AmapWeatherProperties properties, RestClient externalRestClient) {
        return new AmapWeatherProvider(properties, new RestClientAmapWeatherClient(externalRestClient));
    }
    /** Demo 只用于没有真实结果时的明确降级展示。 */
    @Bean WeatherProvider demoWeatherProvider() { return new DemoWeatherProvider(); }
    /** 缓存键仅为影院行政区，不存用户位置或路线。 */
    @Bean WeatherCache weatherCache() { return new InMemoryWeatherCache(); }
}
