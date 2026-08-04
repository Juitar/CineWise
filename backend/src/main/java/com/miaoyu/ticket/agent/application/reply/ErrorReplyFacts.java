package com.miaoyu.ticket.agent.application.reply;

import java.util.List;

/** 失败回复只暴露稳定错误码或计划问题码，不携带异常原文。 */
public record ErrorReplyFacts(Integer errorCode, List<String> issueCodes) implements AgentReplyPayload {

    public ErrorReplyFacts {
        issueCodes = issueCodes == null ? List.of() : List.copyOf(issueCodes);
        if (errorCode == null && issueCodes.isEmpty()) {
            throw new IllegalArgumentException("失败事实必须包含错误码或问题码");
        }
        if (issueCodes.stream().anyMatch(code -> code == null || code.isBlank())) {
            throw new IllegalArgumentException("问题码不能为空");
        }
    }

    @Override
    public boolean supports(AgentReplyMessageType messageType) {
        return messageType == AgentReplyMessageType.ERROR;
    }
}
