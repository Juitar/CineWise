package com.miaoyu.ticket.agent.api;

/** 批量清空中，活动运行会话只计入 skippedCount，不会被强制中断。 */
public record AgentSessionBulkClearResponse(int clearedCount, int skippedCount) {
}
