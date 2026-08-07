package com.miaoyu.ticket.content.infrastructure.provider;

import com.miaoyu.ticket.content.application.ContentProperties;
import java.time.Duration;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Bean;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;
import com.miaoyu.ticket.content.application.LiveContentSyncPort;
import java.time.Clock;
import org.springframework.core.env.Environment;

/**
 * 注册内容模块自己的配置对象。
 *
 * <p>配置注册留在基础设施层，Application 和 Domain 不依赖 Spring 的配置注解。</p>
 */
@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties({ContentProperties.class, NetStartProperties.class})
public class ContentProviderConfiguration {

    /** 独立客户端避免改变其他模块的超时；Provider 默认关闭也不会在启动时发出网络请求。 */
    @Bean
    NetStartRawClient netStartRawClient(NetStartProperties properties) {
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(properties.connectTimeout());
        requestFactory.setReadTimeout(properties.readTimeout());
        return new RestClientNetStartRawClient(RestClient.builder().baseUrl(properties.baseUrl())
                .requestFactory(requestFactory).build());
    }

    @Bean
    NetStartRequestLimiter netStartRequestLimiter(NetStartProperties properties, Clock clock) {
        return new NetStartRequestLimiter(properties, clock);
    }

    @Bean
    LiveContentSyncPort netStartContentSyncPort(NetStartProperties properties, Environment environment,
                                                Clock clock, NetStartRawClient rawClient,
                                                NetStartRequestLimiter requestLimiter) {
        return new NetStartContentProvider(properties, environment, clock, rawClient, requestLimiter);
    }

    /** 排期 Provider 使用独立 HTTP 客户端，避免改变已有影片/影院同步的超时配置。 */
    @Bean
    com.miaoyu.ticket.content.application.ExternalShowtimeProvider externalShowtimeProvider(
            NetStartProperties properties, Environment environment, Clock clock,
            NetStartRequestLimiter requestLimiter) {
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(properties.connectTimeout());
        requestFactory.setReadTimeout(properties.readTimeout());
        RestClient restClient = RestClient.builder().baseUrl(properties.baseUrl())
                .requestFactory(requestFactory).build();
        return new NetStartShowtimeProvider(properties, environment, clock, restClient, requestLimiter);
    }
}
