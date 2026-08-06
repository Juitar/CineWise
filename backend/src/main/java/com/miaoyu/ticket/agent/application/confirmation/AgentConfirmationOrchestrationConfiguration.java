package com.miaoyu.ticket.agent.application.confirmation;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.miaoyu.ticket.agent.application.persistence.AgentRunStepRepository;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** 将已保存的确认节点接到既有建单 action 创建服务，不扩展 A 的确认实现。 */
@Configuration(proxyBeanMethods = false)
public class AgentConfirmationOrchestrationConfiguration {

    @Bean
    public CreateOrderConfirmationCommandFactory createOrderConfirmationCommandFactory(ObjectMapper objectMapper) {
        return new CreateOrderConfirmationCommandFactory(objectMapper);
    }

    @Bean
    public CreateOrderConfirmationActionOrchestrator createOrderConfirmationActionOrchestrator(
            AgentRunStepRepository stepRepository,
            CreateOrderConfirmationCommandFactory commandFactory,
            AgentConfirmationActionCreationService actionCreationService) {
        return new CreateOrderConfirmationActionOrchestrator(stepRepository, commandFactory, actionCreationService);
    }
}
