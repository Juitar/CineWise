package com.miaoyu.ticket.agent;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import com.miaoyu.ticket.agent.application.model.ModelGateway;
import com.miaoyu.ticket.agent.application.run.MinimalReadOnlyAgentService;
import com.miaoyu.ticket.agent.application.tool.AgentToolConfiguration;
import com.miaoyu.ticket.agent.domain.plan.PlanSchemaValidator;
import com.miaoyu.ticket.agent.domain.tool.ToolRegistry;
import com.miaoyu.ticket.recommendation.api.RankMoviePlanTool;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;

/** 验证最小主控通过明确 Java 类型装配，不依赖 Bean 名或字符串路由。 */
class AgentToolConfigurationTest {

    @Test
    void shouldCreateOneTypedDependencyForMinimalReadOnlyAgentService() {
        try (AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext()) {
            context.registerBean(RankMoviePlanTool.class, () -> mock(RankMoviePlanTool.class));
            context.register(AgentToolConfiguration.class);
            context.refresh();

            assertThat(context.getBeansOfType(ToolRegistry.class)).hasSize(1);
            assertThat(context.getBeansOfType(PlanSchemaValidator.class)).hasSize(1);
            assertThat(context.getBeansOfType(ModelGateway.class)).hasSize(1);
            assertThat(context.getBeansOfType(MinimalReadOnlyAgentService.class)).hasSize(1);
        }
    }
}
