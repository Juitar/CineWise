package com.miaoyu.ticket.agent.api;

import java.time.OffsetDateTime;

/** 取消请求返回已保存的当前运行状态，重复取消不产生新状态。 */
public record AgentRunCancelResponse(String runId, String status, OffsetDateTime finishedAt) {
}
