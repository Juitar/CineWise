package com.miaoyu.ticket.agent.application.persistence;

import com.miaoyu.ticket.agent.domain.persistence.AgentEventStreamCursor;
import com.miaoyu.ticket.agent.domain.persistence.AgentSession;
import com.miaoyu.ticket.common.config.ClockConfiguration;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.Objects;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * 清理已到期终态运行的 Agent 记录。
 *
 * <p>一个运行的事件、步骤、消息和运行记录必须在同一短事务按固定子表到父表顺序删除。每次删除前先锁
 * 会话，再锁事件游标，保证它不会与同会话事件追加交错更新保留边界。</p>
 */
@Service
public class AgentEventCleanupService {
    private static final int BATCH_SIZE = 500;
    private final AgentRuntimeEventRepository eventRepository;
    private final AgentSessionRepository sessionRepository;
    private final AgentRunStepRepository stepRepository;
    private final AgentMessageRepository messageRepository;
    private final AgentRunRepository runRepository;
    private final Clock clock;
    private final TransactionTemplate transactionTemplate;

    public AgentEventCleanupService(
            AgentRuntimeEventRepository eventRepository,
            AgentSessionRepository sessionRepository,
            AgentRunStepRepository stepRepository,
            AgentMessageRepository messageRepository,
            AgentRunRepository runRepository,
            Clock clock,
            PlatformTransactionManager transactionManager) {
        this.eventRepository = eventRepository;
        this.sessionRepository = sessionRepository;
        this.stepRepository = stepRepository;
        this.messageRepository = messageRepository;
        this.runRepository = runRepository;
        this.clock = clock;
        this.transactionTemplate = new TransactionTemplate(transactionManager);
    }

    /** 每轮最多选择 500 个已到期终态运行；运行中的会话和运行不会成为候选。 */
    public int cleanupExpiredRuns() {
        LocalDateTime now = LocalDateTime.ofInstant(clock.instant(), ClockConfiguration.BUSINESS_ZONE_ID);
        return eventRepository.findExpiredTerminalRuns(now, BATCH_SIZE).stream()
                .mapToInt(candidate -> Boolean.TRUE.equals(transactionTemplate.execute(
                        ignored -> cleanupOneInTransaction(candidate, now))) ? 1 : 0)
                .sum();
    }

    private boolean cleanupOneInTransaction(AgentExpiredRunCandidate candidate, LocalDateTime now) {
        Objects.requireNonNull(candidate, "到期运行候选不能为空");
        Objects.requireNonNull(now, "清理时间不能为空");
        AgentSession session = sessionRepository
                .findBySessionIdAndUserIdForUpdate(candidate.sessionId(), candidate.userId())
                .orElse(null);
        if (session == null || session.activeRunId() != null) {
            return false;
        }
        AgentEventStreamCursor cursor = eventRepository.findCursorForUpdate(session.sessionId()).orElse(null);

        // 删除顺序必须让追加事件和所有子记录先于 run、cursor 和空会话消失。
        eventRepository.deleteByRunId(candidate.externalRunId());
        stepRepository.deleteByRunId(candidate.runId());
        messageRepository.deleteByRunId(candidate.runId());
        if (!runRepository.deleteTerminalExpiredById(candidate.runId(), now)) {
            throw new IllegalStateException("Agent 到期运行在清理期间发生变化");
        }
        if (runRepository.hasRuns(session.id())) {
            updateRetainedBoundary(cursor, session, now);
        } else {
            eventRepository.deleteCursor(session.sessionId());
            sessionRepository.deleteIfEmptyAndInactive(session.id());
        }
        return true;
    }

    private void updateRetainedBoundary(AgentEventStreamCursor cursor, AgentSession session, LocalDateTime now) {
        if (cursor == null) {
            throw new IllegalStateException("Agent 会话存在运行但缺少事件游标");
        }
        Long firstRetained = eventRepository.findFirstRetainedEventId(session.sessionId());
        AgentEventStreamCursor next = new AgentEventStreamCursor(
                session.sessionId(), cursor.lastCommittedEventId(), firstRetained, cursor.version() + 1,
                session.expireAt(), cursor.createTime(), now);
        if (!eventRepository.updateCursor(next, cursor.version())) {
            throw new IllegalStateException("Agent 事件游标已由其他事务更新");
        }
    }
}
