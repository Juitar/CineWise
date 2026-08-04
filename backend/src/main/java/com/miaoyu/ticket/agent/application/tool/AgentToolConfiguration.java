package com.miaoyu.ticket.agent.application.tool;

import com.miaoyu.ticket.agent.domain.run.ExecutionPlanStateMachine;
import com.miaoyu.ticket.agent.domain.tool.ToolRegistry;
import com.miaoyu.ticket.recommendation.api.RankMoviePlanTool;
import java.util.List;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** B 的工具装配入口，只登记明确的类型化工具。 */
@Configuration
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

    /** 通过构造器明确绑定 D 的公开推荐工具，不提供字符串路由入口。 */
    @Bean
    public RankMoviePlanExecutionAdapter rankMoviePlanExecutionAdapter(
            RankMoviePlanTool rankMoviePlanTool, ExecutionPlanStateMachine executionPlanStateMachine) {
        return new RankMoviePlanExecutionAdapter(rankMoviePlanTool, executionPlanStateMachine);
    }
}
