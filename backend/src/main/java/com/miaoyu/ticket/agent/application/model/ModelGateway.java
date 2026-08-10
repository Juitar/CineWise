package com.miaoyu.ticket.agent.application.model;

import java.util.Objects;
import java.util.function.Consumer;

/** 应用层隔离模型供应商 SDK、鉴权、HTTP 与原始输出的端口。 */
public interface ModelGateway {
    /** 未实现分类的网关不能扩大可调用范围，默认按普通对话处理。 */
    default AgentIntent classifyIntent(IntentClassificationRequest request) {
        return AgentIntent.GENERAL_CHAT;
    }

    PlanGenerationResponse generatePlan(PlanGenerationRequest request);

    ReplyGenerationResponse generateReply(ReplyGenerationRequest request);

    /**
     * 仅用于服务端已经决定为普通 {@code TEXT} 的回复。回调拿到的内容必须已经经过网关的输出安全检查，
     * 调用方仍不得把它反向解析为工具参数或业务事实。
     *
     * <p>默认实现让 Mock 与既有测试保持同步回复语义；真实网关可以覆盖为逐段读取供应商 SSE。</p>
     */
    default ReplyGenerationResponse generateReplyStream(
            ReplyGenerationRequest request, Consumer<String> onTextDelta) {
        ReplyGenerationResponse response = generateReply(request);
        Objects.requireNonNull(onTextDelta, "文本分片回调不能为空").accept(response.text());
        return response;
    }

    /**
     * 当前网关是否已经在每个回调前完成跨分片安全检查。
     *
     * <p>默认 {@code false}，使旧实现仍由主控保留尾部字符再下发；真实流式网关可显式返回
     * {@code true}，避免同一段文本在两层各等待一次而延后首个可见字符。</p>
     */
    default boolean emitsValidatedTextDeltas() {
        return false;
    }
}
