package com.miaoyu.ticket.recommendation.api;

import com.miaoyu.ticket.agent.domain.tool.ToolContext;
import com.miaoyu.ticket.agent.domain.tool.ToolResult;
import com.miaoyu.ticket.agent.domain.tool.ToolStatus;
import com.miaoyu.ticket.common.error.CommonErrorCode;
import com.miaoyu.ticket.recommendation.application.FixedRecommendationQueryService;
import com.miaoyu.ticket.recommendation.application.FixedRecommendationResult;
import java.util.Objects;
import org.springframework.stereotype.Component;

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

    private final FixedRecommendationQueryService queryService;

    public RankMoviePlanTool(FixedRecommendationQueryService queryService) {
        this.queryService = queryService;
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
}
