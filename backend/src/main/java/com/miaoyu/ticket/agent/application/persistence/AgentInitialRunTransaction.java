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
import com.miaoyu.ticket.agent.domain.plan.SlotSnapshot;
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
    /** 首包进度不带 text，前端按既有 message.delta 进度项展示，不能与最终回复正文拼接。 */
    private static final AgentStoredJson INITIAL_PROGRESS_PAYLOAD = new AgentStoredJson("{\"phase\":\"generating\"}");

    private final AgentSessionRepository sessionRepository;
    private final AgentRunRepository runRepository;
    private final AgentMessageRepository messageRepository;
    private final AgentRequestHashFactory requestHashFactory;
    private final AgentRuntimeEventService runtimeEventService;
    private final AgentConversationSlotService conversationSlotService;
    private final BusinessIdGenerator idGenerator;
    private final Clock clock;

    public AgentInitialRunTransaction(
            AgentSessionRepository sessionRepository,
            AgentRunRepository runRepository,
            AgentMessageRepository messageRepository,
            AgentRequestHashFactory requestHashFactory,
            AgentRuntimeEventService runtimeEventService,
            AgentConversationSlotService conversationSlotService,
            BusinessIdGenerator idGenerator,
            Clock clock) {
        this.sessionRepository = sessionRepository;
        this.runRepository = runRepository;
        this.messageRepository = messageRepository;
        this.requestHashFactory = requestHashFactory;
        this.runtimeEventService = runtimeEventService;
        this.conversationSlotService = conversationSlotService;
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
        runtimeEventService.append(session, run, AgentEventType.MESSAGE_DELTA, INITIAL_PROGRESS_PAYLOAD);
        return new AgentInitialRunResult(run, false);
    }

    /** 幂等查询、槽位更新和 run 占用必须在同一把会话行锁及同一短事务内完成。 */
    @Transactional
    public AgentInitialRunResult submitConversation(long userId, String sessionId, String content,
            String clientRequestId, String entry) {
        AgentSession session = sessionRepository.findBySessionIdAndUserIdForUpdate(sessionId, userId)
                .orElseThrow(() -> new BusinessException(AgentErrorCode.AGENT_RESOURCE_NOT_FOUND));
        LocalDateTime now = LocalDateTime.ofInstant(clock.instant(), ClockConfiguration.BUSINESS_ZONE_ID);
        if (session.status() != AgentSessionStatus.ACTIVE || !session.expireAt().isAfter(now)) {
            throw new BusinessException(AgentErrorCode.AGENT_RESOURCE_NOT_FOUND);
        }
        AgentConversationSlotService.PreparedSlots prepared = conversationSlotService
                .prepareLocked(session, userId, content, entry);
        SlotSnapshot slotSnapshot = prepared.snapshot();
        AgentRequestHash requestHash = requestHashFactory.create(content, slotSnapshot);
        AgentRun existing = runRepository.findByClientRequestId(userId, session.id(), clientRequestId).orElse(null);
        if (existing != null) {
            if (!existing.requestHash().equals(requestHash)) {
                // 重放会在首个请求已提交槽位后再次计算快照；内容相同仍必须返回原运行，
                // 但不同内容复用同一 clientRequestId 仍然是冲突，不能借此覆盖原请求。
                boolean sameOriginalContent = messageRepository.findByRunIdAndUserId(existing.id(), userId).stream()
                        .filter(message -> message.role() == AgentMessageRole.USER)
                        .map(AgentMessage::text)
                        .findFirst()
                        .map(content::equals)
                        .orElse(false);
                if (!sameOriginalContent) {
                    throw new BusinessException(AgentErrorCode.REQUEST_HASH_MISMATCH);
                }
            }
            return new AgentInitialRunResult(existing, true, slotSnapshot, prepared.conversationContext());
        }
        conversationSlotService.persistLocked(session, userId, prepared);
        long runId = idGenerator.nextId();
        LocalDateTime expireAt = now.plusDays(RETENTION_DAYS);
        String traceId = TraceIdHolder.currentTraceId();
        if (traceId.isBlank()) {
            traceId = UUID.randomUUID().toString().replace("-", "");
        }
        AgentRun run = new AgentRun(
                runId, UUID.randomUUID().toString(), session.id(), userId, clientRequestId, requestHash,
                null, null, AgentRunStatus.RUNNING, traceId, now, null, 0L, now, now, expireAt);
        try {
            runRepository.insert(run);
        } catch (DuplicateKeyException exception) {
            throw new AgentConcurrentDuplicateRequestException(requestHash, exception);
        }
        messageRepository.insert(new AgentMessage(
                idGenerator.nextId(), UUID.randomUUID().toString(), session.id(), runId, userId,
                AgentMessageRole.USER, AgentMessageType.TEXT, content, userMessagePayload(entry),
                AgentMessageStatus.COMPLETED,
                now, now, expireAt));
        sessionRepository.setSummaryIfAbsent(session.id(), userId, sessionSummary(content), now);
        if (!sessionRepository.claimActiveRun(session.id(), userId, runId, expireAt)) {
            throw new BusinessException(AgentErrorCode.ACTIVE_RUN_CONFLICT);
        }
        runtimeEventService.append(session, run, AgentEventType.MESSAGE_START,
                new AgentStoredJson("{\"phase\":\"accepted\"}"));
        runtimeEventService.append(session, run, AgentEventType.MESSAGE_DELTA, INITIAL_PROGRESS_PAYLOAD);
        return new AgentInitialRunResult(run, false, slotSnapshot, prepared.conversationContext());
    }

    /** 只保存受控展示来源；普通输入保持旧 payload，其他任意 entry 也不能伪装成追问回答。 */
    private static AgentStoredJson userMessagePayload(String entry) {
        return "question".equals(entry) ? new AgentStoredJson("{\"entry\":\"question\"}") : null;
    }

    private static String sessionSummary(String content) {
        String normalized = content == null ? "" : content.strip().replaceAll("\\s+", " ");
        if (normalized.length() <= 80) {
            return normalized;
        }
        return normalized.substring(0, 80) + "…";
    }
}
