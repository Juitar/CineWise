package com.miaoyu.ticket.agent;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.miaoyu.ticket.agent.application.persistence.AgentSessionCreationService;
import com.miaoyu.ticket.agent.application.persistence.AgentSessionRepository;
import com.miaoyu.ticket.agent.domain.persistence.AgentSession;
import com.miaoyu.ticket.agent.domain.persistence.AgentSessionStatus;
import com.miaoyu.ticket.auth.application.CurrentUserAccessor;
import com.miaoyu.ticket.common.id.BusinessIdGenerator;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;

/** 会话创建必须从认证上下文取得用户，而非相信请求中的 userId。 */
class AgentSessionCreationServiceTest {

    @Test
    void shouldCreateActiveSessionForCurrentUser() {
        CurrentUserAccessor currentUserAccessor = Mockito.mock(CurrentUserAccessor.class);
        AgentSessionRepository sessionRepository = Mockito.mock(AgentSessionRepository.class);
        BusinessIdGenerator idGenerator = () -> 9001L;
        when(currentUserAccessor.requireCurrentUserId()).thenReturn(7L);
        Clock clock = Clock.fixed(Instant.parse("2026-08-04T02:00:00Z"), ZoneId.of("Asia/Shanghai"));
        AgentSessionCreationService service =
                new AgentSessionCreationService(currentUserAccessor, sessionRepository, idGenerator, clock);

        AgentSession result = service.createSession();

        ArgumentCaptor<AgentSession> sessionCaptor = ArgumentCaptor.forClass(AgentSession.class);
        verify(sessionRepository).insert(sessionCaptor.capture());
        assertEquals(9001L, result.id());
        assertEquals(7L, result.userId());
        assertEquals(AgentSessionStatus.ACTIVE, result.status());
        assertNull(result.activeRunId());
        assertEquals(result, sessionCaptor.getValue());
        assertEquals(result.createTime().plusDays(30), result.expireAt());
    }
}
