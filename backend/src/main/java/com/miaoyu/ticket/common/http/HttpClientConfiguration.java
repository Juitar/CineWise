package com.miaoyu.ticket.common.http;

import com.miaoyu.ticket.common.config.HttpClientProperties;
import java.net.http.HttpClient;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

/** 外部 Provider 共用的无自动业务重试 HTTP 客户端。 */
@Configuration(proxyBeanMethods = false)
public class HttpClientConfiguration {

    @Bean
    public RestClient externalRestClient(RestClient.Builder builder, HttpClientProperties properties) {
        HttpClient httpClient = HttpClient.newBuilder()
                .connectTimeout(properties.connectTimeout())
                .followRedirects(HttpClient.Redirect.NEVER)
                .build();
        JdkClientHttpRequestFactory requestFactory = new JdkClientHttpRequestFactory(httpClient);
        requestFactory.setReadTimeout(properties.readTimeout());
        return builder.requestFactory(requestFactory).build();
    }
}
