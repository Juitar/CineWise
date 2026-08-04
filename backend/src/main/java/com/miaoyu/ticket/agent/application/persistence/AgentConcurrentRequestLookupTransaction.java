package com.miaoyu.ticket.agent.application.persistence;

import com.miaoyu.ticket.agent.application.AgentErrorCode;
import com.miaoyu.ticket.agent.domain.persistence.AgentRequestHash;
import com.miaoyu.ticket.agent.domain.persistence.AgentRun;
import com.miaoyu.ticket.agent.domain.persistence.AgentSession;
import com.miaoyu.ticket.common.error.BusinessException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/** 唯一键冲突后的独立读取事务，不能复用 MySQL REPEATABLE READ 的旧快照。 */
@Service
public class AgentConcurrentRequestLookupTransaction {
    private final AgentSessionRepository sessionRepository;
    private final AgentRunRepository runRepository;

    public AgentConcurrentRequestLookupTransaction(
            AgentSessionRepository sessionRepository, AgentRunRepository runRepository) {
        this.sessionRepository = sessionRepository;
        this.runRepository = runRepository;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW, readOnly = true)
    public AgentRun findWinner(long userId, AgentMessageSubmissionCommand command, AgentRequestHash requestHash) {
        AgentSession session = sessionRepository.findBySessionIdAndUserId(command.sessionId(), userId)
                .orElseThrow(() -> new BusinessException(AgentErrorCode.AGENT_RESOURCE_NOT_FOUND));
        AgentRun winner = runRepository.findByClientRequestId(userId, session.id(), command.clientRequestId())
                .orElseThrow(() -> new IllegalStateException("唯一键冲突后未找到已提交运行"));
        if (!winner.requestHash().equals(requestHash)) {
            throw new BusinessException(AgentErrorCode.REQUEST_HASH_MISMATCH);
        }
        return winner;
    }
}
