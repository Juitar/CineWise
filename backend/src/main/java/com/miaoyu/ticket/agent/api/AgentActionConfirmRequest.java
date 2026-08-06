package com.miaoyu.ticket.agent.api;

import jakarta.validation.constraints.NotNull;

/** 确认请求只允许表达同意或拒绝，其他交易字段由服务端 action 取得。 */
public record AgentActionConfirmRequest(@NotNull Boolean confirmed) {
}
