package com.miaoyu.ticket.agent.application.model;

/** 意图识别只接收脱敏后的当前文本，不能据此生成 Tool 参数。 */
public record IntentClassificationRequest(String input) {
    public IntentClassificationRequest {
        if (input == null || input.isBlank()) {
            throw new IllegalArgumentException("input 不能为空");
        }
    }
}
