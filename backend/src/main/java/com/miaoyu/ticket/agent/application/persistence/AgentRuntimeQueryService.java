package com.miaoyu.ticket.agent.application.persistence;

import com.miaoyu.ticket.agent.application.AgentErrorCode;
import com.miaoyu.ticket.agent.domain.persistence.AgentMessage;
import com.miaoyu.ticket.agent.domain.persistence.AgentRun;
import com.miaoyu.ticket.agent.domain.persistence.AgentSession;
import com.miaoyu.ticket.agent.domain.persistence.AgentRunStep;
import com.miaoyu.ticket.agent.domain.persistence.AgentRuntimeEvent;
import com.miaoyu.ticket.auth.application.CurrentUserAccessor;
import com.miaoyu.ticket.common.error.BusinessException;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 为当前用户组装可恢复的运行快照，不暴露模型原文或工具原始参数。 */
@Service
public class AgentRuntimeQueryService {
    private static final int EVENT_LIMIT = 500;
    private final CurrentUserAccessor currentUserAccessor;
    private final AgentRunRepository runRepository;
    private final AgentSessionRepository sessionRepository;
    private final AgentMessageRepository messageRepository;
    private final AgentRunStepRepository stepRepository;
    private final AgentRuntimeEventRepository eventRepository;

    public AgentRuntimeQueryService(CurrentUserAccessor currentUserAccessor, AgentRunRepository runRepository,
            AgentSessionRepository sessionRepository,
            AgentMessageRepository messageRepository, AgentRunStepRepository stepRepository,
            AgentRuntimeEventRepository eventRepository) {
        this.currentUserAccessor = currentUserAccessor;
        this.runRepository = runRepository;
        this.sessionRepository = sessionRepository;
        this.messageRepository = messageRepository;
        this.stepRepository = stepRepository;
        this.eventRepository = eventRepository;
    }

    @Transactional(readOnly = true)
    public RuntimeView queryMyRun(String runId) {
        long userId = currentUserAccessor.requireCurrentUserId();
        AgentRun run = runRepository.findByRunIdAndUserId(runId, userId)
                .orElseThrow(() -> new BusinessException(AgentErrorCode.AGENT_RESOURCE_NOT_FOUND));
        AgentSession session = sessionRepository.findByIdAndUserId(run.sessionId(), userId)
                .orElseThrow(() -> new BusinessException(AgentErrorCode.AGENT_RESOURCE_NOT_FOUND));
        List<AgentRuntimeEvent> events = eventRepository.findByRunId(run.runId(), 0L, EVENT_LIMIT);
        long lastEventId = eventRepository.findLastEventIdByRunId(run.runId());
        return new RuntimeView(run, session, messageRepository.findByRunIdAndUserId(run.id(), userId),
                stepRepository.findByRunId(run.id()), events, lastEventId);
    }

    /** 初始化 POST 结果未知时按同一会话和请求键只读恢复，不允许客户端重发写请求。 */
    @Transactional(readOnly = true)
    public RuntimeView queryMyRunByClientRequest(String sessionId, String clientRequestId) {
        long userId = currentUserAccessor.requireCurrentUserId();
        AgentSession session = sessionRepository.findBySessionIdAndUserId(sessionId, userId)
                .orElseThrow(() -> new BusinessException(AgentErrorCode.AGENT_RESOURCE_NOT_FOUND));
        AgentRun run = runRepository.findByClientRequestId(userId, session.id(), clientRequestId)
                .orElseThrow(() -> new BusinessException(AgentErrorCode.AGENT_RESOURCE_NOT_FOUND));
        List<AgentRuntimeEvent> events = eventRepository.findByRunId(run.runId(), 0L, EVENT_LIMIT);
        return new RuntimeView(run, session, messageRepository.findByRunIdAndUserId(run.id(), userId),
                stepRepository.findByRunId(run.id()), events, eventRepository.findLastEventIdByRunId(run.runId()));
    }

    public record RuntimeView(AgentRun run, AgentSession session, List<AgentMessage> messages, List<AgentRunStep> steps,
            List<AgentRuntimeEvent> events, long lastEventId) {
    }
}
