package com.miaoyu.ticket.agent.application.persistence;

import com.miaoyu.ticket.agent.domain.persistence.AgentRequestHash;

/** 唯一键冲突必须先回滚初始写事务，再由外层在新事务中读取已提交的胜者。 */
public class AgentConcurrentDuplicateRequestException extends RuntimeException {
    private final AgentRequestHash requestHash;

    public AgentConcurrentDuplicateRequestException(AgentRequestHash requestHash, Throwable cause) {
        super(cause);
        this.requestHash = requestHash;
    }

    public AgentRequestHash requestHash() {
        return requestHash;
    }
}
