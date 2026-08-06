package com.miaoyu.ticket.agent.infrastructure.model;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.miaoyu.ticket.agent.application.model.ModelGateway;
import com.miaoyu.ticket.agent.domain.plan.PlanSchemaValidator;
import java.time.Duration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

/** 真实 DeepSeek 网关的基础设施装配，避免应用层直接依赖 Web 客户端。 */
@Configuration(proxyBeanMethods = false)
@ConditionalOnProperty(prefix = "cinewise.agent.deepseek", name = "enabled", havingValue = "true")
@EnableConfigurationProperties(DeepSeekProperties.class)
public class DeepSeekModelConfiguration {

    @Bean
    ModelGateway deepSeekModelGateway(
            DeepSeekProperties properties, ObjectMapper objectMapper, PlanSchemaValidator planSchemaValidator) {
        properties.requireEnabledConfiguration();
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        Duration timeout = properties.timeout();
        requestFactory.setConnectTimeout(timeout);
        requestFactory.setReadTimeout(timeout);
        RestClient client = RestClient.builder().baseUrl(properties.baseUrl())
                .requestFactory(requestFactory).build();
        return new DeepSeekModelGateway(client, properties, objectMapper, planSchemaValidator);
    }
}
