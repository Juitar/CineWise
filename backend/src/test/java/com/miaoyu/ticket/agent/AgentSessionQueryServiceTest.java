package com.miaoyu.ticket.agent;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.miaoyu.ticket.agent.application.persistence.AgentRunRepository;
import com.miaoyu.ticket.agent.application.persistence.AgentSessionQueryService;
import com.miaoyu.ticket.agent.application.persistence.AgentSessionRepository;
import com.miaoyu.ticket.agent.domain.persistence.AgentRequestHash;
import com.miaoyu.ticket.agent.domain.persistence.AgentRun;
import com.miaoyu.ticket.agent.domain.persistence.AgentRunStatus;
import com.miaoyu.ticket.agent.domain.persistence.AgentSession;
import com.miaoyu.ticket.agent.domain.persistence.AgentSessionStatus;
import com.miaoyu.ticket.auth.application.CurrentUserAccessor;
import com.miaoyu.ticket.common.error.BusinessException;
import java.time.LocalDateTime;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

/** 查询服务必须将认证用户 ID 传入 Repository，避免读取后才发现越权。 */
class AgentSessionQueryServiceTest {
    private static final LocalDateTime NOW = LocalDateTime.of(2026, 8, 4, 10, 0);

    @Test
    void shouldQueryOnlySessionOwnedByCurrentUser() {
        CurrentUserAccessor currentUserAccessor = Mockito.mock(CurrentUserAccessor.class);
        AgentSessionRepository sessionRepository = Mockito.mock(AgentSessionRepository.class);
        AgentRunRepository runRepository = Mockito.mock(AgentRunRepository.class);
        when(currentUserAccessor.requireCurrentUserId()).thenReturn(7L);
        when(sessionRepository.findBySessionIdAndUserId("session-1", 7L)).thenReturn(Optional.of(session()));
        AgentSessionQueryService service =
                new AgentSessionQueryService(currentUserAccessor, sessionRepository, runRepository);

        AgentSession result = service.querySession("session-1");

        assertEquals("session-1", result.sessionId());
        verify(sessionRepository).findBySessionIdAndUserId("session-1", 7L);
    }

    @Test
    void shouldHideOtherUsersRunAsNotFound() {
        CurrentUserAccessor currentUserAccessor = Mockito.mock(CurrentUserAccessor.class);
        AgentSessionRepository sessionRepository = Mockito.mock(AgentSessionRepository.class);
        AgentRunRepository runRepository = Mockito.mock(AgentRunRepository.class);
        when(currentUserAccessor.requireCurrentUserId()).thenReturn(7L);
        when(runRepository.findByRunIdAndUserId("run-other-user", 7L)).thenReturn(Optional.empty());
        AgentSessionQueryService service =
                new AgentSessionQueryService(currentUserAccessor, sessionRepository, runRepository);

        BusinessException exception = assertThrows(BusinessException.class, () -> service.queryRun("run-other-user"));

        assertEquals(206005, exception.getErrorCode().code());
        verify(runRepository).findByRunIdAndUserId("run-other-user", 7L);
    }

    @SuppressWarnings("UnusedMethod")
    private static AgentRun run() {
        return new AgentRun(
                2L,
                "run-1",
                1L,
                7L,
                "request-1",
                new AgentRequestHash("v1", "a".repeat(64)),
                null,
                null,
                AgentRunStatus.RUNNING,
                "trace-1",
                NOW,
                null,
                0L,
                NOW,
                NOW,
                NOW.plusDays(30));
    }

    private static AgentSession session() {
        return new AgentSession(
                1L,
                "session-1",
                7L,
                null,
                AgentSessionStatus.ACTIVE,
                null,
                0L,
                NOW,
                NOW,
                NOW.plusDays(30));
    }
}
