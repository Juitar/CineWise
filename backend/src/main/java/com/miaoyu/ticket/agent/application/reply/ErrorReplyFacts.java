package com.miaoyu.ticket.agent.application.reply;

import java.util.List;

/**
 * 失败回复只暴露稳定错误码或计划问题码，不携带异常原文。
 *
 * <p>异常 message、堆栈、模型原始响应和下游请求参数都可能包含实现或用户信息，只能记录在受控日志
 * 中并由 traceId 查询；它们不能进入回复模型或对外文案。
 *
 * @param errorCode 下游已映射的稳定错误码；计划校验失败时可以为空
 * @param issueCodes 服务端计划校验问题码，保留给内部展示和测试，构造时不可变复制
 */
public record ErrorReplyFacts(Integer errorCode, List<String> issueCodes) implements AgentReplyPayload {

    public ErrorReplyFacts {
        issueCodes = issueCodes == null ? List.of() : List.copyOf(issueCodes);
        // 至少保留一个稳定原因，避免调用方收到无法分类的空 ERROR 回复。
        if (errorCode == null && issueCodes.isEmpty()) {
            throw new IllegalArgumentException("失败事实必须包含错误码或问题码");
        }
        // 空问题码无法被调用方统计或映射，也不允许模型用空白内容掩盖校验失败。
        if (issueCodes.stream().anyMatch(code -> code == null || code.isBlank())) {
            throw new IllegalArgumentException("问题码不能为空");
        }
    }

    @Override
    public boolean supports(AgentReplyMessageType messageType) {
        return messageType == AgentReplyMessageType.ERROR;
    }
}
