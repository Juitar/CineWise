package com.miaoyu.ticket.recommendation.api;

import com.miaoyu.ticket.agent.domain.tool.ToolContext;
import com.miaoyu.ticket.agent.domain.tool.ToolResult;
import com.miaoyu.ticket.agent.domain.tool.ToolStatus;
import com.miaoyu.ticket.common.error.CommonErrorCode;
import com.miaoyu.ticket.recommendation.application.FixedRecommendationQueryService;
import com.miaoyu.ticket.recommendation.application.FixedRecommendationResult;
import com.miaoyu.ticket.recommendation.application.PersonalizedRecommendationQueryService;
import com.miaoyu.ticket.recommendation.domain.RecommendationPlanResult;
import java.util.Objects;
import org.springframework.stereotype.Component;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * D 提供给 B 的推荐工具适配器。
 *
 * <p>它只把类型化 Command 交给推荐应用服务并包装结果；不访问 Mapper、不追问用户、不调用模型、不发布
 * SSE，也不生成 PLAN_CARD。</p>
 */
@Component
public class RankMoviePlanTool {

    /*
     * 适配器只负责三件事：校验 targetName、转换 Command、包装 ToolResult。
     * 票务事实查询在 Application Service 内完成。
     * Agent 运行状态、SSE 和卡片构造都不属于本类。
     * 这样 B 未来替换路由实现时，不会影响 D 的推荐规则。
     * 工具失败也不应回显用户条件或内部异常详情。
     * 只读工具不需要幂等键，且不能产生任何写入副作用。
     * 公共时效窗口直接来自 D 的查询结果。
     * 业务字段只放在类型化 data 中。
     * 工具不保存用户画像。
     * 工具不创建订单。
     * 工具不锁定座位。
     * 工具不修改场次状态。
     * 工具不调用模型。
     * 工具不发送事件。
     * 工具调用可被 B 安全重复读取。
     * 工具不读取 Cookie。
     * 工具不读取 JWT。
     * 工具不记录用户输入。
     * 工具不返回敏感数据。
     * 工具结果供 B 决定后续展示。
     */

    public static final String TARGET_NAME = "rankMoviePlan";

    // 旧查询服务只为已上线的固定推荐调用保留，完整入口不能用它补造方案。
    private final FixedRecommendationQueryService queryService;
    // 完整服务聚合内容和 A 的可售场次，是新结果唯一可信的计算来源。
    private final PersonalizedRecommendationQueryService personalizedQueryService;

    // 旧构造器只留给现有 B 适配器和历史测试，生产容器必须选择下面的完整依赖构造器。
    public RankMoviePlanTool(FixedRecommendationQueryService queryService) {
        this(queryService, null);
    }

    /** 新版完整条件查询；旧构造器保留给现有 B 测试和兼容入口。 */
    @Autowired
    public RankMoviePlanTool(FixedRecommendationQueryService queryService,
            PersonalizedRecommendationQueryService personalizedQueryService) {
        // 两个服务分开保存，避免完整推荐切换期间改变旧工具的查询语义。
        this.queryService = queryService;
        this.personalizedQueryService = personalizedQueryService;
    }

    /**
     * 执行只读推荐查询。
     *
     * <p>当前 B 尚未实现 ToolRouter 时，D 的单元测试可直接调用本方法；将来路由只需按 targetName 绑定，
     * 不需要让 D 参与运行状态或 SSE。</p>
     */
    public ToolResult<FixedRecommendationResult> execute(ToolContext context, RankMoviePlanCommand command) {
        // 工具上下文只用于调用边界校验，不能被传入推荐领域对象。
        Objects.requireNonNull(context, "context 不能为空");
        Objects.requireNonNull(command, "command 不能为空");
        if (!TARGET_NAME.equals(context.targetName())) {
            // 目标名称不匹配时拒绝调用，防止 B 的错误路由误执行推荐查询。
            return new ToolResult<>(
                    ToolStatus.FAILED,
                    null,
                    CommonErrorCode.INVALID_PARAMETER.code(),
                    false,
                    false,
                    "CHECK_TOOL_TARGET",
                    false,
                    null,
                    context.stateVersion(),
                    null,
                    null);
        }
        FixedRecommendationResult result = queryService.query(command.toQuery());
        // 无场次时仍是成功的只读查询；degraded 让主控选择展示不可购候选或继续收集条件。
        // 卡片、SSE 和运行记录仍由 B 的执行引擎负责，D 不在这里创建任何 Agent 状态。
        return new ToolResult<>(
                ToolStatus.SUCCESS,
                result,
                null,
                false,
                false,
                "RENDER_RESULT",
                !result.purchaseEligible(),
                result.purchaseEligible() ? null : "SHOWTIME_UNAVAILABLE",
                context.stateVersion(),
                result.dataAt(),
                result.expiresAt());
    }

    /**
     * 为 B 4.2 对接准备的完整推荐入口。
     *
     * <p>该入口只接受包含城市、日期和人数的完整 Command，并始终调用完整推荐查询。当前 Agent
     * 白名单和运行适配器仍使用旧 {@link #execute(ToolContext, RankMoviePlanCommand)}；只有 B 完成
     * 4.2 的注册表和适配器切换后，本入口才是实际运行入口。</p>
     */
    public ToolResult<RecommendationPlanResult> executeRecommendationPlan(
            ToolContext context, RankMoviePlanCommand command) {
        // 上下文只提供 B 已验证的运行元数据，用户条件只能来自类型化 Command。
        Objects.requireNonNull(context, "context 不能为空");
        Objects.requireNonNull(command, "command 不能为空");
        if (!TARGET_NAME.equals(context.targetName())) {
            // 错误路由不能触发内容和票务查询，避免其它工具节点意外得到推荐结果。
            return new ToolResult<>(ToolStatus.FAILED, null, CommonErrorCode.INVALID_PARAMETER.code(), false, false,
                    "CHECK_TOOL_TARGET", false, null, context.stateVersion(), null, null);
        }
        if (command.cityCode() == null || command.cityCode().isBlank()) {
            // 完整方案不能回退到旧固定查询，否则 B 会把缺少城市和人数的结果误当成可展示方案。
            return new ToolResult<>(ToolStatus.FAILED, null, CommonErrorCode.INVALID_PARAMETER.code(), false, false,
                    "COMPLETE_CONSTRAINTS_REQUIRED", false, null, context.stateVersion(), null, null);
        }
        if (personalizedQueryService == null) {
            // 这是错误的测试装配或 Bean 装配，不把内部异常或固定推荐结果泄露给调用方。
            return new ToolResult<>(ToolStatus.FAILED, null, CommonErrorCode.INTERNAL_ERROR.code(), false, false,
                    "RETRY_LATER", false, null, context.stateVersion(), null, null);
        }
        // 只把 D 的完整计算结果原样交给 B，卡片、SSE 和 Agent 状态仍由 B 负责。
        RecommendationPlanResult result = personalizedQueryService.query(command.toConstraints());
        // 空方案或放宽建议仍是成功查询；降级标识只描述结果可用性，不能变成自动重试。
        return new ToolResult<>(ToolStatus.SUCCESS, result, null, false, false, "RENDER_RESULT",
                result.degraded(), result.degraded() ? "RECOMMENDATION_DEGRADED" : null,
                context.stateVersion(), result.dataAt(), result.expiresAt());
    }

}
