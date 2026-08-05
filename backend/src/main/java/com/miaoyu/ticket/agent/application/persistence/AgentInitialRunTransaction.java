package com.miaoyu.ticket.agent.application.persistence;

import com.miaoyu.ticket.agent.application.AgentErrorCode;
import com.miaoyu.ticket.agent.domain.persistence.AgentMessage;
import com.miaoyu.ticket.agent.domain.persistence.AgentMessageRole;
import com.miaoyu.ticket.agent.domain.persistence.AgentMessageStatus;
import com.miaoyu.ticket.agent.domain.persistence.AgentMessageType;
import com.miaoyu.ticket.agent.domain.persistence.AgentRequestHash;
import com.miaoyu.ticket.agent.domain.persistence.AgentRun;
import com.miaoyu.ticket.agent.domain.persistence.AgentRunStatus;
import com.miaoyu.ticket.agent.domain.persistence.AgentSession;
import com.miaoyu.ticket.agent.domain.persistence.AgentSessionStatus;
import com.miaoyu.ticket.agent.domain.persistence.AgentEventType;
import com.miaoyu.ticket.agent.domain.persistence.AgentStoredJson;
import com.miaoyu.ticket.common.config.ClockConfiguration;
import com.miaoyu.ticket.common.error.BusinessException;
import com.miaoyu.ticket.common.id.BusinessIdGenerator;
import com.miaoyu.ticket.common.observability.TraceIdHolder;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.UUID;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 初始运行短事务；不调用模型或 D 的工具。 */
@Service
public class AgentInitialRunTransaction {
    private static final int RETENTION_DAYS = 30;

    private final AgentSessionRepository sessionRepository;
    private final AgentRunRepository runRepository;
    private final AgentMessageRepository messageRepository;
    private final AgentRequestHashFactory requestHashFactory;
    private final AgentRuntimeEventService runtimeEventService;
    private final BusinessIdGenerator idGenerator;
    private final Clock clock;

    public AgentInitialRunTransaction(
            AgentSessionRepository sessionRepository,
            AgentRunRepository runRepository,
            AgentMessageRepository messageRepository,
            AgentRequestHashFactory requestHashFactory,
            AgentRuntimeEventService runtimeEventService,
            BusinessIdGenerator idGenerator,
            Clock clock) {
        this.sessionRepository = sessionRepository;
        this.runRepository = runRepository;
        this.messageRepository = messageRepository;
        this.requestHashFactory = requestHashFactory;
        this.runtimeEventService = runtimeEventService;
        this.idGenerator = idGenerator;
        this.clock = clock;
    }

    /** 插入运行和用户消息后条件占用会话；占用失败抛出异常使整个事务回滚。 */
    @Transactional
    public AgentInitialRunResult submit(long userId, AgentMessageSubmissionCommand command) {
        AgentSession session = sessionRepository.findBySessionIdAndUserId(command.sessionId(), userId)
                .orElseThrow(() -> new BusinessException(AgentErrorCode.AGENT_RESOURCE_NOT_FOUND));
        if (session.status() != AgentSessionStatus.ACTIVE) {
            throw new BusinessException(AgentErrorCode.AGENT_RESOURCE_NOT_FOUND);
        }
        AgentRequestHash requestHash = requestHashFactory.create(command.content(), command.slotSnapshot());
        AgentRun existing = runRepository
                .findByClientRequestId(userId, session.id(), command.clientRequestId())
                .orElse(null);
        if (existing != null) {
            if (!existing.requestHash().equals(requestHash)) {
                throw new BusinessException(AgentErrorCode.REQUEST_HASH_MISMATCH);
            }
            return new AgentInitialRunResult(existing, true);
        }
        LocalDateTime now = LocalDateTime.ofInstant(clock.instant(), ClockConfiguration.BUSINESS_ZONE_ID);
        long runId = idGenerator.nextId();
        LocalDateTime expireAt = now.plusDays(RETENTION_DAYS);
        String traceId = TraceIdHolder.currentTraceId();
        if (traceId.isBlank()) {
            traceId = UUID.randomUUID().toString().replace("-", "");
        }
        AgentRun run = new AgentRun(
                runId, UUID.randomUUID().toString(), session.id(), userId, command.clientRequestId(), requestHash,
                null, null, AgentRunStatus.RUNNING, traceId, now, null, 0L, now, now, expireAt);
        try {
            runRepository.insert(run);
        } catch (DuplicateKeyException exception) {
            // MySQL REPEATABLE READ 下当前事务可能看不到胜者，必须回滚后再由外层新事务读取。
            throw new AgentConcurrentDuplicateRequestException(requestHash, exception);
        }
        messageRepository.insert(new AgentMessage(
                idGenerator.nextId(), UUID.randomUUID().toString(), session.id(), runId, userId,
                AgentMessageRole.USER, AgentMessageType.TEXT, command.content(), null, AgentMessageStatus.COMPLETED,
                now, now, expireAt));
        if (!sessionRepository.claimActiveRun(session.id(), userId, runId, expireAt)) {
            throw new BusinessException(AgentErrorCode.ACTIVE_RUN_CONFLICT);
        }
        runtimeEventService.append(session, run, AgentEventType.MESSAGE_START,
                new AgentStoredJson("{\"phase\":\"accepted\"}"));
        return new AgentInitialRunResult(run, false);
    }
}
