package com.miaoyu.ticket.agent;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.miaoyu.ticket.agent.application.AgentErrorCode;
import com.miaoyu.ticket.agent.application.persistence.AgentMessageRepository;
import com.miaoyu.ticket.agent.application.persistence.AgentRunRepository;
import com.miaoyu.ticket.agent.application.persistence.AgentSessionManagementService;
import com.miaoyu.ticket.agent.application.persistence.AgentSessionRepository;
import com.miaoyu.ticket.agent.domain.persistence.AgentSession;
import com.miaoyu.ticket.agent.domain.persistence.AgentSessionStatus;
import com.miaoyu.ticket.agent.domain.persistence.AgentMessage;
import com.miaoyu.ticket.agent.domain.persistence.AgentMessageRole;
import com.miaoyu.ticket.agent.domain.persistence.AgentMessageStatus;
import com.miaoyu.ticket.agent.domain.persistence.AgentMessageType;
import com.miaoyu.ticket.agent.domain.persistence.AgentRequestHash;
import com.miaoyu.ticket.agent.domain.persistence.AgentRun;
import com.miaoyu.ticket.agent.domain.persistence.AgentRunStatus;
import com.miaoyu.ticket.agent.domain.persistence.AgentStoredJson;
import com.miaoyu.ticket.auth.application.CurrentUserAccessor;
import com.miaoyu.ticket.common.error.BusinessException;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

/** 会话控制只读取认证用户，清空依赖数据库条件更新而非 JVM 内存判断。 */
class AgentSessionManagementServiceTest {
    private static final long USER_ID = 7L;
    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-08-05T04:00:00Z"), ZoneId.of("Asia/Shanghai"));

    @Test
    void shouldListOnlyActiveSessionsForCurrentUser() {
        CurrentUserAccessor currentUser = currentUser();
        AgentSessionRepository sessions = Mockito.mock(AgentSessionRepository.class);
        AgentMessageRepository messages = Mockito.mock(AgentMessageRepository.class);
        when(sessions.countActiveByUserId(USER_ID)).thenReturn(1L);
        when(sessions.findActiveByUserId(USER_ID, 0, 20)).thenReturn(List.of(session("session-1", null)));
        AgentSessionManagementService service = service(currentUser, sessions, messages);

        var result = service.listMySessions(1, 20);

        assertThat(result.total()).isEqualTo(1L);
        assertThat(result.records()).extracting(AgentSession::sessionId).containsExactly("session-1");
        verify(sessions).findActiveByUserId(USER_ID, 0, 20);
    }

    @Test
    void shouldHideClearedSessionMessagesAsNotFound() {
        CurrentUserAccessor currentUser = currentUser();
        AgentSessionRepository sessions = Mockito.mock(AgentSessionRepository.class);
        AgentMessageRepository messages = Mockito.mock(AgentMessageRepository.class);
        when(sessions.findBySessionIdAndUserId("cleared", USER_ID))
                .thenReturn(Optional.of(new AgentSession(1L, "cleared", USER_ID, null, AgentSessionStatus.CLEARED,
                        null, 1L, java.time.LocalDateTime.of(2026, 8, 5, 12, 0),
                        java.time.LocalDateTime.of(2026, 8, 5, 12, 0),
                        java.time.LocalDateTime.of(2026, 9, 4, 12, 0))));
        AgentSessionManagementService service = service(currentUser, sessions, messages);

        assertThatThrownBy(() -> service.listMySessionMessages("cleared", 1, 20))
                .isInstanceOf(BusinessException.class)
                .extracting(exception -> ((BusinessException) exception).getErrorCode())
                .isEqualTo(AgentErrorCode.AGENT_RESOURCE_NOT_FOUND);
    }

    @Test
    void shouldHideOtherUsersSessionMessagesWithoutLookingUpRuns() {
        CurrentUserAccessor currentUser = currentUser();
        AgentSessionRepository sessions = Mockito.mock(AgentSessionRepository.class);
        AgentMessageRepository messages = Mockito.mock(AgentMessageRepository.class);
        AgentRunRepository runs = Mockito.mock(AgentRunRepository.class);
        when(sessions.findBySessionIdAndUserId("other-user-session", USER_ID)).thenReturn(Optional.empty());
        AgentSessionManagementService service =
                new AgentSessionManagementService(currentUser, sessions, messages, runs, CLOCK);

        assertThatThrownBy(() -> service.listMySessionMessages("other-user-session", 1, 20))
                .isInstanceOf(BusinessException.class)
                .extracting(exception -> ((BusinessException) exception).getErrorCode())
                .isEqualTo(AgentErrorCode.AGENT_RESOURCE_NOT_FOUND);
        verifyNoInteractions(messages, runs);
    }

    @Test
    void shouldMapConfirmationCardAndOtherMessagesToOwnedPublicRunIdsInOneBatch() {
        CurrentUserAccessor currentUser = currentUser();
        AgentSessionRepository sessions = Mockito.mock(AgentSessionRepository.class);
        AgentMessageRepository messages = Mockito.mock(AgentMessageRepository.class);
        AgentRunRepository runs = Mockito.mock(AgentRunRepository.class);
        AgentSession session = session("session-1", null);
        when(sessions.findBySessionIdAndUserId("session-1", USER_ID)).thenReturn(Optional.of(session));
        when(messages.countBySessionIdAndUserId(1L, USER_ID)).thenReturn(3L);
        when(messages.findBySessionIdAndUserId(1L, USER_ID, 0, 20))
                .thenReturn(List.of(message("message-1", 101L), message("confirmation-card", 102L),
                        message("message-3", 101L)));
        List<AgentRun> ownedRuns = List.of(run(101L, "52b810c5-4b03-4a41-9c36-07372f1a6f59"),
                run(102L, "34e7e610-f3b9-408a-8f8e-8134d89f7b7a"));
        when(runs.findByIdsAndUserIdAndSessionId(List.of(101L, 102L), USER_ID, 1L)).thenReturn(ownedRuns);
        AgentSessionManagementService service =
                new AgentSessionManagementService(currentUser, sessions, messages, runs, CLOCK);

        var result = service.listMySessionMessages("session-1", 1, 20);

        assertThat(result.records()).extracting(AgentSessionManagementService.MessageRecord::runId)
                .containsExactly("52b810c5-4b03-4a41-9c36-07372f1a6f59", "34e7e610-f3b9-408a-8f8e-8134d89f7b7a",
                        "52b810c5-4b03-4a41-9c36-07372f1a6f59");
        verify(runs).findByIdsAndUserIdAndSessionId(List.of(101L, 102L), USER_ID, 1L);
    }

    @Test
    void shouldRejectClearingSessionWithActiveRun() {
        CurrentUserAccessor currentUser = currentUser();
        AgentSessionRepository sessions = Mockito.mock(AgentSessionRepository.class);
        AgentMessageRepository messages = Mockito.mock(AgentMessageRepository.class);
        when(sessions.findBySessionIdAndUserId("session-1", USER_ID))
                .thenReturn(Optional.of(session("session-1", 99L)));
        AgentSessionManagementService service = service(currentUser, sessions, messages);

        assertThatThrownBy(() -> service.clearMySession("session-1"))
                .isInstanceOf(BusinessException.class)
                .extracting(exception -> ((BusinessException) exception).getErrorCode())
                .isEqualTo(AgentErrorCode.ACTIVE_RUN_CONFLICT);
    }

    @Test
    void shouldClearOnlyInactiveSessionAndExpireItsData() {
        CurrentUserAccessor currentUser = currentUser();
        AgentSessionRepository sessions = Mockito.mock(AgentSessionRepository.class);
        AgentMessageRepository messages = Mockito.mock(AgentMessageRepository.class);
        AgentSession session = session("session-1", null);
        when(sessions.findBySessionIdAndUserId("session-1", USER_ID)).thenReturn(Optional.of(session));
        when(sessions.clearIfActiveAndInactive(eq(1L), eq(USER_ID), any())).thenReturn(true);
        AgentSessionManagementService service = service(currentUser, sessions, messages);

        var result = service.clearMySession("session-1");

        assertThat(result.cleared()).isTrue();
        verify(sessions).expireSessionData(eq(1L), eq("session-1"), any());
    }

    @Test
    void shouldSkipActiveSessionsDuringBulkClear() {
        CurrentUserAccessor currentUser = currentUser();
        AgentSessionRepository sessions = Mockito.mock(AgentSessionRepository.class);
        AgentMessageRepository messages = Mockito.mock(AgentMessageRepository.class);
        AgentSession idle = session("idle", null);
        when(sessions.findAllActiveByUserId(USER_ID)).thenReturn(List.of(idle, session("running", 9L)));
        when(sessions.clearIfActiveAndInactive(eq(1L), eq(USER_ID), any())).thenReturn(true);
        AgentSessionManagementService service = service(currentUser, sessions, messages);

        var result = service.clearMySessions();

        assertThat(result.clearedCount()).isEqualTo(1);
        assertThat(result.skippedCount()).isEqualTo(1);
        verify(sessions).expireSessionData(eq(1L), eq("idle"), any());
    }

    private static CurrentUserAccessor currentUser() {
        CurrentUserAccessor currentUser = Mockito.mock(CurrentUserAccessor.class);
        when(currentUser.requireCurrentUserId()).thenReturn(USER_ID);
        return currentUser;
    }

    private static AgentSessionManagementService service(
            CurrentUserAccessor currentUser, AgentSessionRepository sessions, AgentMessageRepository messages) {
        return new AgentSessionManagementService(
                currentUser, sessions, messages, Mockito.mock(AgentRunRepository.class), CLOCK);
    }

    private static AgentSession session(String sessionId, Long activeRunId) {
        java.time.LocalDateTime now = java.time.LocalDateTime.of(2026, 8, 5, 12, 0);
        return new AgentSession(1L, sessionId, USER_ID, null, AgentSessionStatus.ACTIVE, activeRunId, 0L,
                now, now, now.plusDays(30));
    }

    private static AgentMessage message(String messageId, long runId) {
        java.time.LocalDateTime now = java.time.LocalDateTime.of(2026, 8, 5, 12, 0);
        return new AgentMessage(runId + 1_000L, messageId, 1L, runId, USER_ID, AgentMessageRole.ASSISTANT,
                AgentMessageType.TEXT, "确认", new AgentStoredJson("{}"), AgentMessageStatus.COMPLETED,
                now, now, now.plusDays(30));
    }

    private static AgentRun run(long id, String runId) {
        java.time.LocalDateTime now = java.time.LocalDateTime.of(2026, 8, 5, 12, 0);
        return new AgentRun(id, runId, 1L, USER_ID, "request-" + id,
                new AgentRequestHash("v1", "a".repeat(64)), null, null, AgentRunStatus.RUNNING,
                "trace", now, null, 0L, now, now, now.plusDays(30));
    }
}
