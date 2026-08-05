package com.miaoyu.ticket.order.api;

import com.miaoyu.ticket.agent.domain.tool.ToolContext;
import com.miaoyu.ticket.agent.domain.tool.ToolResult;
import com.miaoyu.ticket.agent.domain.tool.ToolStatus;
import com.miaoyu.ticket.common.config.ClockConfiguration;
import com.miaoyu.ticket.common.error.BusinessException;
import com.miaoyu.ticket.common.error.CommonErrorCode;
import com.miaoyu.ticket.order.application.CreateOrderCommand;
import com.miaoyu.ticket.order.application.OrderApplicationService;
import com.miaoyu.ticket.order.application.OrderView;
import java.math.RoundingMode;
import java.time.OffsetDateTime;
import java.util.Objects;
import org.springframework.stereotype.Component;

/**
 * A 对 B 暴露的已确认建单工具适配器。
 *
 * <p>本类只做边界校验、类型转换和结果包装；库存、金额、当前用户、事务和幂等最终由订单应用服务及
 * MySQL 负责。B 的 actionId 校验不在此复制，避免形成第二套确认消费逻辑。</p>
 *
 * <p>安全与恢复约束：</p>
 * <ul>
 *   <li>targetName 必须固定为 createOrder，不能把模型字符串解释成 Spring Bean 名；</li>
 *   <li>用户只由 CurrentUserAccessor 间接取得，Command 不提供 userId；</li>
 *   <li>clientRequestId 与 idempotencyKey 只来自 ToolContext，并由订单唯一约束兜底；</li>
 *   <li>SUCCESS 只表示订单应用服务已返回权威订单，不根据输入自行拼装成功；</li>
 *   <li>FAILED 仅携带稳定错误码，不把异常消息或数据库细节交给 Agent；</li>
 *   <li>PROCESSING 表示写结果无法确认，retryable 固定为 false；</li>
 *   <li>结果未知后只能调用 queryByClientRequestId，禁止再次执行 execute；</li>
 *   <li>Adapter 不发布 SSE、不推进运行节点，也不读取 B 的确认持久化。</li>
 * </ul>
 */
@Component
public class CreateOrderTool {

    public static final String TARGET_NAME = "createOrder";
    public static final String QUERY_ORIGINAL_ORDER = "QUERY_ORIGINAL_ORDER";

    private final OrderApplicationService orderApplicationService;

    public CreateOrderTool(OrderApplicationService orderApplicationService) {
        this.orderApplicationService = orderApplicationService;
    }

    /**
     * 执行一次已确认建单；同一上下文的结果未知时只能走查询方法，不能在此处自动重发写请求。
     *
     * <p>Command 的 actionId 已由 B 消费，本方法仍重新校验 Tool target、请求键和业务 ID；确认通过
     * 不代表座位仍可售，A 的订单事务必须再次读取场次、服务端价格和全部座位状态。</p>
     */
    public ToolResult<AgentOrderResult> execute(
            ToolContext context,
            CreateOrderForAgentCommand command) {
        Objects.requireNonNull(context, "context 不能为空");
        Objects.requireNonNull(command, "command 不能为空");
        // 目标不匹配时在任何订单服务调用前失败，防止路由配置错误触发真实锁座。
        if (!TARGET_NAME.equals(context.targetName())) {
            return failed(CommonErrorCode.INVALID_PARAMETER.code(), context, "CHECK_TOOL_TARGET");
        }
        try {
            // 写请求标识由 B 在一次确认意图内保持稳定；缺失时不能临时生成替代 UUID。
            context.requireWriteRequestIdentifiers();
            // 此处只转换类型，排序、去重、金额和库存仍由 OrderApplicationService 重新校验。
            OrderView order = orderApplicationService.createOrder(new CreateOrderCommand(
                    command.parsedShowId(),
                    command.parsedSeatIds(),
                    context.clientRequestId(),
                    context.idempotencyKey()));
            return success(order);
        } catch (IllegalArgumentException exception) {
            // 参数失败不可重试；B 应重新检查受信任槽位与确认动作，而不是重复调用工具。
            return failed(CommonErrorCode.INVALID_PARAMETER.code(), context, "CHECK_INPUT");
        } catch (BusinessException exception) {
            // 只传稳定数值错误码，座位冲突、场次停售和幂等冲突由 B 按冻结契约展示。
            return failed(exception.getErrorCode().code(), context, "CHECK_ORDER_STATE");
        } catch (RuntimeException exception) {
            // 数据库或事务边界无法确认时保持 PROCESSING，交给 B 通过原 clientRequestId 读恢复。
            return processing(context);
        }
    }

    /**
     * 按当前认证用户和原 clientRequestId 恢复订单；身份不接受 Tool 参数中的 userId。
     *
     * <p>查询不要求再次提供 idempotencyKey，因为恢复键是已落库的 clientRequestId；查询仍通过
     * OrderApplicationService 获取当前用户，跨用户命中与不存在统一表现为订单不存在。</p>
     */
    public ToolResult<AgentOrderResult> queryByClientRequestId(ToolContext context) {
        Objects.requireNonNull(context, "context 不能为空");
        // 查询仍绑定 createOrder 工具，避免其他工具借恢复入口探测订单。
        if (!TARGET_NAME.equals(context.targetName())) {
            return failed(CommonErrorCode.INVALID_PARAMETER.code(), context, "CHECK_TOOL_TARGET");
        }
        try {
            // 恢复必须复用原键；空白或超长键直接失败，不能用新的请求标识替代。
            requireClientRequestId(context.clientRequestId());
            return success(orderApplicationService.queryByClientRequestId(context.clientRequestId()));
        } catch (IllegalArgumentException exception) {
            return failed(CommonErrorCode.INVALID_PARAMETER.code(), context, "CHECK_INPUT");
        } catch (BusinessException exception) {
            // 订单不存在保持 FAILED，B 可结束恢复或重新引导用户确认，不自动创建新订单。
            return failed(exception.getErrorCode().code(), context, "QUERY_ORIGINAL_ORDER");
        } catch (RuntimeException exception) {
            // 查询本身结果未知时仍禁止把查询异常升级为新的建单写请求。
            return processing(context);
        }
    }

    private ToolResult<AgentOrderResult> success(OrderView order) {
        // ToolResult.stateVersion 使用实际订单版本，供 B 丢弃旧结果，而不是沿用计划输入版本。
        AgentOrderResult result = toResult(order);
        return new ToolResult<>(
                ToolStatus.SUCCESS,
                result,
                null,
                false,
                false,
                "CONTINUE_ORDER_FLOW",
                false,
                null,
                (long) order.stateVersion(),
                null,
                null);
    }

    private ToolResult<AgentOrderResult> failed(
            int errorCode,
            ToolContext context,
            String nextAction) {
        // 所有业务失败都不可由工具自动重试；是否重新规划由 B 根据错误码和用户选择决定。
        return new ToolResult<>(
                ToolStatus.FAILED,
                null,
                errorCode,
                false,
                false,
                nextAction,
                false,
                null,
                context.stateVersion(),
                null,
                null);
    }

    private ToolResult<AgentOrderResult> processing(ToolContext context) {
        // PROCESSING 没有 data 和 errorCode，唯一安全动作是按原 clientRequestId 查询权威订单。
        return new ToolResult<>(
                ToolStatus.PROCESSING,
                null,
                null,
                false,
                false,
                QUERY_ORIGINAL_ORDER,
                false,
                null,
                context.stateVersion(),
                null,
                null);
    }

    private void requireClientRequestId(String clientRequestId) {
        // 与页面建单恢复入口保持同一 64 字符上限，避免 Agent 路径形成另一套键规则。
        if (clientRequestId == null || clientRequestId.isBlank() || clientRequestId.length() > 64) {
            throw new IllegalArgumentException("clientRequestId 不合法");
        }
    }

    private AgentOrderResult toResult(OrderView order) {
        // 对外类型只保留 B 后续渲染和支付跳转所需字段，不暴露用户、哈希或持久化对象。
        return new AgentOrderResult(
                Long.toString(order.orderId()),
                order.orderNo(),
                Long.toString(order.showId()),
                order.seatIds().stream().map(String::valueOf).toList(),
                order.ticketCount(),
                order.unitPrice().setScale(2, RoundingMode.UNNECESSARY).toPlainString(),
                order.totalAmount().setScale(2, RoundingMode.UNNECESSARY).toPlainString(),
                order.status().name(),
                toOffsetDateTime(order.expireTime()),
                order.stateVersion(),
                toOffsetDateTime(order.updatedAt()));
    }

    private OffsetDateTime toOffsetDateTime(java.time.LocalDateTime value) {
        // Agent 与页面共享业务时区；B 如需 UTC 可在 SSE 序列化边界统一转换，A 不改写业务瞬间。
        return value.atZone(ClockConfiguration.BUSINESS_ZONE_ID).toOffsetDateTime();
    }
}
