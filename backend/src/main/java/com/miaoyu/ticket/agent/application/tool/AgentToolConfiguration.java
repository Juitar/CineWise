package com.miaoyu.ticket.agent.application.tool;

import com.miaoyu.ticket.agent.application.model.ModelGateway;
import com.miaoyu.ticket.agent.application.run.MinimalReadOnlyAgentService;
import com.miaoyu.ticket.agent.application.run.MultiToolSupervisor;
import com.miaoyu.ticket.agent.domain.plan.PlanSchemaValidator;
import com.miaoyu.ticket.agent.domain.run.ExecutionPlanStateMachine;
import com.miaoyu.ticket.agent.domain.tool.ToolRegistry;
import com.miaoyu.ticket.agent.infrastructure.model.MockModelGateway;
import com.miaoyu.ticket.recommendation.api.RankMoviePlanTool;
import java.util.List;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * B 的工具装配入口，只登记明确的类型化工具。
 *
 * <p>这里故意不提供按 Bean 名、类名或工具名字符串查找的通用入口。Agent 计划属于不可信输入，
 * 工具可用范围只能由代码中的 {@link AgentToolDefinitions} 明确登记。
 */
@Configuration(proxyBeanMethods = false)
public class AgentToolConfiguration {

    /**
     * 当前白名单只有已经完成 B-D 接口确认的推荐查询工具。
     *
     * <p>新增工具必须同时补充类型定义、适配器、计划校验和测试；只把工具名加入此处会使模型可生成
     * 无法安全执行的计划。
     */
    @Bean
    public ToolRegistry agentToolRegistry() {
        return new ToolRegistry(List.of(AgentToolDefinitions.rankMoviePlan(), AgentToolDefinitions.createOrder()));
    }

    /**
     * 状态机仍只负责状态推进，真实工具调用留在应用层适配器。
     *
     * <p>这样状态机可以单独测试重试和下游跳过规则，不能因为注入 D 的工具而越过 B-D 的应用 API 边界。
     */
    @Bean
    public ExecutionPlanStateMachine executionPlanStateMachine(ToolRegistry agentToolRegistry) {
        return new ExecutionPlanStateMachine(agentToolRegistry);
    }

    /**
     * 所有模型候选计划都复用同一个服务端白名单校验器。
     *
     * <p>校验器必须与运行时注册表使用同一份定义，避免“生成时允许、执行时拒绝”或相反的结果。
     */
    @Bean
    public PlanSchemaValidator planSchemaValidator(ToolRegistry agentToolRegistry) {
        return new PlanSchemaValidator(agentToolRegistry);
    }

    /**
     * 当前开发和演示环境使用确定性 Mock，真实模型仍只能实现 ModelGateway。
     *
     * <p>业务主控依赖端口而非 Mock 的具体类，后续替换真实模型时不能让 SDK 响应穿透到运行状态或工具层。
     */
    @Bean
    public MockModelGateway mockModelGateway(
            PlanSchemaValidator planSchemaValidator, ToolRegistry agentToolRegistry) {
        return new MockModelGateway(planSchemaValidator, agentToolRegistry);
    }

    /**
     * 通过构造器明确绑定 D 的公开推荐工具，不提供字符串路由入口。
     *
     * <p>适配器只调用 {@code RankMoviePlanTool.execute(context, command)}；它不能调用 D 的 Controller、
     * Repository 或持久化对象，也不参与计划生成和状态机推进。
     */
    @Bean
    public RankMoviePlanExecutionAdapter rankMoviePlanExecutionAdapter(
            RankMoviePlanTool rankMoviePlanTool, ExecutionPlanStateMachine executionPlanStateMachine) {
        return new RankMoviePlanExecutionAdapter(rankMoviePlanTool, executionPlanStateMachine);
    }

    /**
     * 最小主控只组合 B 已有组件，不通过字符串或 Bean 名选择工具。
     *
     * <p>构造器依赖使每个参与者的职责可见：模型端口产生候选、校验器审核候选、状态机推进节点，
     * 适配器执行唯一的已登记工具。
     */
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

    /** 多工具主控与既有最小流程并存，提交入口完成持久化重规划接入后切换到该 Bean。 */
    @Bean
    public MultiToolSupervisor multiToolSupervisor(
            ModelGateway modelGateway,
            ToolRegistry agentToolRegistry,
            PlanSchemaValidator planSchemaValidator,
            ExecutionPlanStateMachine executionPlanStateMachine,
            RankMoviePlanExecutionAdapter rankMoviePlanExecutionAdapter) {
        return new MultiToolSupervisor(
                modelGateway,
                agentToolRegistry,
                planSchemaValidator,
                executionPlanStateMachine,
                rankMoviePlanExecutionAdapter);
    }
}
