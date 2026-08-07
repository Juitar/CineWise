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
import com.miaoyu.ticket.agent.domain.persistence.AgentStoredJson;
import com.miaoyu.ticket.agent.domain.plan.SlotSnapshot;
import com.miaoyu.ticket.auth.application.CurrentUserAccessor;
import com.miaoyu.ticket.common.config.ClockConfiguration;
import com.miaoyu.ticket.common.observability.TraceIdHolder;
import com.miaoyu.ticket.common.error.BusinessException;
import com.miaoyu.ticket.common.id.BusinessIdGenerator;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 原子创建等待定位运行；不调用模型、推荐工具或位置服务。 */
@Service
public class AgentDistanceRunInitializationService {
    private static final int RETENTION_DAYS = 30;

    private final CurrentUserAccessor users;
    private final AgentSessionRepository sessions;
    private final AgentRunRepository runs;
    private final AgentMessageRepository messages;
    private final AgentRequestHashFactory hashes;
    private final BusinessIdGenerator ids;
    private final Clock clock;

    public AgentDistanceRunInitializationService(CurrentUserAccessor users, AgentSessionRepository sessions,
            AgentRunRepository runs, AgentMessageRepository messages, AgentRequestHashFactory hashes,
            BusinessIdGenerator ids, Clock clock) {
        this.users = users;
        this.sessions = sessions;
        this.runs = runs;
        this.messages = messages;
        this.hashes = hashes;
        this.ids = ids;
        this.clock = clock;
    }
    @Transactional
    public AgentInitialRunResult initialize(String sessionId, String clientRequestId, String content, String entry) {
        long userId = users.requireCurrentUserId();
        AgentSession session = sessions.findBySessionIdAndUserId(sessionId, userId)
                .orElseThrow(() -> new BusinessException(AgentErrorCode.AGENT_RESOURCE_NOT_FOUND));
        if (session.status() != AgentSessionStatus.ACTIVE) {
            throw new BusinessException(AgentErrorCode.AGENT_RESOURCE_NOT_FOUND);
        }
        AgentRequestHash hash = hashes.create(content, new SlotSnapshot(1L, java.util.Map.of("context.entry", entry)));
        AgentRun existing = runs.findByClientRequestId(userId, session.id(), clientRequestId).orElse(null);
        if (existing != null) {
            if (!existing.requestHash().equals(hash)) {
                throw new BusinessException(AgentErrorCode.REQUEST_HASH_MISMATCH);
            }
            return new AgentInitialRunResult(existing, true);
        }
        LocalDateTime now = LocalDateTime.ofInstant(clock.instant(), ClockConfiguration.BUSINESS_ZONE_ID);
        long id = ids.nextId();
        String traceId = TraceIdHolder.currentTraceId();
        if (traceId.isBlank()) {
            traceId = UUID.randomUUID().toString().replace("-", "");
        }
        AgentRun run = new AgentRun(id, UUID.randomUUID().toString(), session.id(), userId, clientRequestId, hash,
                null, null, AgentRunStatus.WAITING_LOCATION, traceId, now, null, 0L, now, now,
                now.plusDays(RETENTION_DAYS));
        // WAITING_LOCATION 的 update_time 由 MySQL CURRENT_TIMESTAMP(3) 写入，是唯一等待起点。
        runs.insertWaitingLocation(run);
        messages.insert(new AgentMessage(ids.nextId(), UUID.randomUUID().toString(), session.id(), id, userId,
                AgentMessageRole.USER, AgentMessageType.TEXT, content,
                new AgentStoredJson("{\"entry\":\"" + entry + "\"}"), AgentMessageStatus.COMPLETED,
                now, now, run.expireAt()));
        if (!sessions.claimActiveRun(session.id(), userId, id, run.expireAt())) {
            throw new BusinessException(AgentErrorCode.ACTIVE_RUN_CONFLICT);
        }
        return new AgentInitialRunResult(run, false);
    }
}
