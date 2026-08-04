package com.miaoyu.ticket.agent.application.run;

import com.miaoyu.ticket.agent.domain.plan.PlanValidationContext;
import java.util.Objects;

/**
 * 最小只读主控的可信应用请求，不包含用户身份、会话或模型候选计划。
 *
 * <p>validationContext 由外层应用服务用服务端槽位和工具定义构造。调用者不能把模型计划放入请求来
 * 跳过复校验，也不能借此对象把前端传来的 userId 当成可信身份。
 *
 * @param clientRequestId 本次请求关联标识，不替代未来写操作所需的幂等键
 * @param input 当前用户输入，模型可阅读但工具只使用确认槽位
 * @param validationContext 服务端提供的槽位、类型和工具校验上下文
 * @param runId 运行关联标识，仅在本次同步结果中传递，不代表已持久化会话
 * @param traceId 诊断关联标识，后续日志和错误输出应使用它排查
 * @param remainingDeadlineMs 调用方剩余时间预算，适配器据此限制下游只读调用
 */
public record MinimalReadOnlyAgentRequest(
        String clientRequestId,
        String input,
        PlanValidationContext validationContext,
        String runId,
        String traceId,
        long remainingDeadlineMs) {

    public MinimalReadOnlyAgentRequest {
        requireText(clientRequestId, "clientRequestId");
        requireText(input, "input");
        validationContext = Objects.requireNonNull(validationContext, "validationContext 不能为空");
        requireText(runId, "runId");
        requireText(traceId, "traceId");
        // 没有剩余预算时不应启动工具调用，否则可能在请求已超时后仍占用下游资源。
        if (remainingDeadlineMs <= 0L) {
            throw new IllegalArgumentException("remainingDeadlineMs 必须大于 0");
        }
    }

    private static void requireText(String value, String fieldName) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(fieldName + " 不能为空");
        }
    }
}
