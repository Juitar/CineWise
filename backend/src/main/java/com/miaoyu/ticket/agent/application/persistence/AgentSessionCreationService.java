package com.miaoyu.ticket.agent.application.persistence;

import com.miaoyu.ticket.agent.domain.persistence.AgentSession;
import com.miaoyu.ticket.agent.domain.persistence.AgentSessionStatus;
import com.miaoyu.ticket.auth.application.CurrentUserAccessor;
import com.miaoyu.ticket.common.config.ClockConfiguration;
import com.miaoyu.ticket.common.id.BusinessIdGenerator;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 创建当前用户的空 Agent 会话；首次运行会将会话到期时间更新为运行到期时间。 */
@Service
public class AgentSessionCreationService {
    private static final int RETENTION_DAYS = 30;

    private final CurrentUserAccessor currentUserAccessor;
    private final AgentSessionRepository sessionRepository;
    private final BusinessIdGenerator idGenerator;
    private final Clock clock;

    public AgentSessionCreationService(
            CurrentUserAccessor currentUserAccessor,
            AgentSessionRepository sessionRepository,
            BusinessIdGenerator idGenerator,
            Clock clock) {
        this.currentUserAccessor = currentUserAccessor;
        this.sessionRepository = sessionRepository;
        this.idGenerator = idGenerator;
        this.clock = clock;
    }

    /** 创建 ACTIVE 会话，不从调用方接收 userId、内部 ID 或到期时间。 */
    @Transactional
    public AgentSession createSession() {
        long userId = currentUserAccessor.requireCurrentUserId();
        LocalDateTime now = LocalDateTime.ofInstant(clock.instant(), ClockConfiguration.BUSINESS_ZONE_ID);
        AgentSession session = new AgentSession(
                idGenerator.nextId(),
                UUID.randomUUID().toString(),
                userId,
                null,
                AgentSessionStatus.ACTIVE,
                null,
                0L,
                now,
                now,
                now.plusDays(RETENTION_DAYS));
        sessionRepository.insert(session);
        return session;
    }
}
