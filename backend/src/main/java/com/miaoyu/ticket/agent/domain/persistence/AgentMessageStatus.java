package com.miaoyu.ticket.agent.domain.persistence;

/** V008 消息为一次写入，数据库中只允许已完成状态。 */
public enum AgentMessageStatus {
    COMPLETED
}
