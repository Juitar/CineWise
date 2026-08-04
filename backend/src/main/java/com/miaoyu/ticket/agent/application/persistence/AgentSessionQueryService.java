package com.miaoyu.ticket.agent.application.persistence;

import com.miaoyu.ticket.agent.domain.persistence.AgentRun;
import com.miaoyu.ticket.agent.domain.persistence.AgentSession;
import com.miaoyu.ticket.auth.application.CurrentUserAccessor;
import com.miaoyu.ticket.common.error.BusinessException;
import com.miaoyu.ticket.agent.application.AgentErrorCode;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 当前用户读取 Agent 会话和运行的应用入口，不从调用参数信任 userId。 */
@Service
public class AgentSessionQueryService {
    private final CurrentUserAccessor currentUserAccessor;
    private final AgentSessionRepository sessionRepository;
    private final AgentRunRepository runRepository;

    public AgentSessionQueryService(
            CurrentUserAccessor currentUserAccessor,
            AgentSessionRepository sessionRepository,
            AgentRunRepository runRepository) {
        this.currentUserAccessor = currentUserAccessor;
        this.sessionRepository = sessionRepository;
        this.runRepository = runRepository;
    }

    /** 查询本人会话；其他用户的会话与不存在统一返回资源不存在。 */
    @Transactional(readOnly = true)
    public AgentSession querySession(String sessionId) {
        long userId = currentUserAccessor.requireCurrentUserId();
        return sessionRepository.findBySessionIdAndUserId(sessionId, userId)
                .orElseThrow(() -> new BusinessException(AgentErrorCode.AGENT_RESOURCE_NOT_FOUND));
    }

    /** 查询本人运行；Repository 必须将 userId 作为 SQL 条件，而不是读取后再判断。 */
    @Transactional(readOnly = true)
    public AgentRun queryRun(String runId) {
        long userId = currentUserAccessor.requireCurrentUserId();
        return runRepository.findByRunIdAndUserId(runId, userId)
                .orElseThrow(() -> new BusinessException(AgentErrorCode.AGENT_RESOURCE_NOT_FOUND));
    }
}
