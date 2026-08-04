package com.miaoyu.ticket.agent.application.model;

import com.miaoyu.ticket.agent.application.reply.AgentReplyMessageType;
import com.miaoyu.ticket.agent.application.reply.AgentReplyPayload;
import java.util.Objects;

/**
 * 只把当前请求和已经过服务端筛选的回复事实交给模型网关。
 *
 * <p>回复生成只能润色已有事实，不能重新选择工具、补全场次或判断交易状态。特别是该对象没有
 * {@code confirmedSlots}，避免模型在回复阶段接触足以重新构造工具命令的输入。
 *
 * @param clientRequestId 用于把模型回复关联到当前调用，不携带用户身份
 * @param input 当前用户输入，供模型保持上下文语气，不是可信业务数据
 * @param requestedType 服务端已决定的回复卡片类型，模型不得自行切换
 * @param payload 已收窄的结构化事实，类型必须与 requestedType 一一匹配
 */
public record ReplyGenerationRequest(
        String clientRequestId,
        String input,
        AgentReplyMessageType requestedType,
        AgentReplyPayload payload) {

    public ReplyGenerationRequest {
        if (clientRequestId == null || clientRequestId.isBlank()) {
            throw new IllegalArgumentException("clientRequestId 不能为空");
        }
        if (input == null || input.isBlank()) {
            throw new IllegalArgumentException("input 不能为空");
        }
        requestedType = Objects.requireNonNull(requestedType, "requestedType 不能为空");
        payload = Objects.requireNonNull(payload, "payload 不能为空");
        // 防止把错误事实包装成推荐卡，或把无场次降级结果伪装为可购计划卡。
        if (!payload.supports(requestedType)) {
            throw new IllegalArgumentException("回复类型与载荷不匹配");
        }
    }
}
