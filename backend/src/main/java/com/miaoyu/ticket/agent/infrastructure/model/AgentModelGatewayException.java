package com.miaoyu.ticket.agent.infrastructure.model;

/** 模型边界的安全失败；调用方只能据此映射稳定错误码，不能返回供应商原文。 */
public class AgentModelGatewayException extends RuntimeException {
    public AgentModelGatewayException(String message, Throwable cause) {
        super(message, cause);
    }

    public AgentModelGatewayException(String message) {
        super(message);
    }
}
