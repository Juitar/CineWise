package com.miaoyu.ticket.agent.application.tool;

import com.miaoyu.ticket.agent.application.model.ModelGateway;
import com.miaoyu.ticket.agent.application.run.MinimalReadOnlyAgentService;
import com.miaoyu.ticket.agent.domain.plan.PlanSchemaValidator;
import com.miaoyu.ticket.agent.domain.run.ExecutionPlanStateMachine;
import com.miaoyu.ticket.agent.domain.tool.ToolRegistry;
import com.miaoyu.ticket.agent.infrastructure.model.MockModelGateway;
import com.miaoyu.ticket.recommendation.api.RankMoviePlanTool;
import java.util.List;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** B 的工具装配入口，只登记明确的类型化工具。 */
@Configuration(proxyBeanMethods = false)
public class AgentToolConfiguration {

    /** 当前白名单只有已经完成 B-D 接口确认的推荐查询工具。 */
    @Bean
    public ToolRegistry agentToolRegistry() {
        return new ToolRegistry(List.of(AgentToolDefinitions.rankMoviePlan()));
    }

    /** 状态机仍只负责状态推进，真实工具调用留在应用层适配器。 */
    @Bean
    public ExecutionPlanStateMachine executionPlanStateMachine(ToolRegistry agentToolRegistry) {
        return new ExecutionPlanStateMachine(agentToolRegistry);
    }

    /** 所有模型候选计划都复用同一个服务端白名单校验器。 */
    @Bean
    public PlanSchemaValidator planSchemaValidator(ToolRegistry agentToolRegistry) {
        return new PlanSchemaValidator(agentToolRegistry);
    }

    /** 当前开发和演示环境使用确定性 Mock，真实模型仍只能实现 ModelGateway。 */
    @Bean
    public MockModelGateway mockModelGateway(
            PlanSchemaValidator planSchemaValidator, ToolRegistry agentToolRegistry) {
        return new MockModelGateway(planSchemaValidator, agentToolRegistry);
    }

    /** 通过构造器明确绑定 D 的公开推荐工具，不提供字符串路由入口。 */
    @Bean
    public RankMoviePlanExecutionAdapter rankMoviePlanExecutionAdapter(
            RankMoviePlanTool rankMoviePlanTool, ExecutionPlanStateMachine executionPlanStateMachine) {
        return new RankMoviePlanExecutionAdapter(rankMoviePlanTool, executionPlanStateMachine);
    }

    /** 最小主控只组合 B 已有组件，不通过字符串或 Bean 名选择工具。 */
    @Bean
    public MinimalReadOnlyAgentService minimalReadOnlyAgentService(
            ModelGateway modelGateway,
            ToolRegistry agentToolRegistry,
            PlanSchemaValidator planSchemaValidator,
            ExecutionPlanStateMachine executionPlanStateMachine,
            RankMoviePlanExecutionAdapter rankMoviePlanExecutionAdapter) {
        return new MinimalReadOnlyAgentService(
                modelGateway,
                agentToolRegistry,
                planSchemaValidator,
                executionPlanStateMachine,
                rankMoviePlanExecutionAdapter);
    }
}
