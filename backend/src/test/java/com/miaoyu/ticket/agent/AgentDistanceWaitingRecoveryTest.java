package com.miaoyu.ticket.agent;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.miaoyu.ticket.agent.application.AgentDistanceRecommendationApplicationService;
import com.miaoyu.ticket.agent.application.AgentDistanceContextApplicationService;
import com.miaoyu.ticket.agent.application.persistence.AgentDistanceRunInitializationService;
import com.miaoyu.ticket.agent.application.persistence.AgentRunRepository;
import com.miaoyu.ticket.agent.application.persistence.AgentRunResultTransaction;
import com.miaoyu.ticket.agent.application.persistence.AgentRuntimeQueryService;
import com.miaoyu.ticket.agent.application.run.MultiToolSupervisor;
import com.miaoyu.ticket.agent.application.run.MultiToolSupervisorResult;
import com.miaoyu.ticket.agent.domain.persistence.AgentRequestHash;
import com.miaoyu.ticket.agent.domain.persistence.AgentMessage;
import com.miaoyu.ticket.agent.domain.persistence.AgentMessageRole;
import com.miaoyu.ticket.agent.domain.persistence.AgentMessageStatus;
import com.miaoyu.ticket.agent.domain.persistence.AgentMessageType;
import com.miaoyu.ticket.agent.domain.persistence.AgentStoredJson;
import com.miaoyu.ticket.agent.domain.persistence.AgentRun;
import com.miaoyu.ticket.agent.domain.persistence.AgentRunStatus;
import com.miaoyu.ticket.auth.application.CurrentUserAccessor;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;

/** 普通入口触发的等待恢复必须真正执行一次，并受过期等待 CAS 保护。 */
class AgentDistanceWaitingRecoveryTest {
    private static final long USER_ID = 7L;
    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-08-07T06:00:00Z"),
            ZoneId.of("Asia/Shanghai"));

    @Test
    void shouldRecoverExpiredWaitingRunAndContinueSameRun() {
        Fixture fixture = fixture();
        AgentRun waiting = run(AgentRunStatus.WAITING_LOCATION);
        when(fixture.runs.findExpiredWaitingLocationByUser(USER_ID, 100)).thenReturn(List.of(waiting));
        when(fixture.runs.recoverExpiredWaitingLocationWithCas(any(), eq(waiting.version()))).thenReturn(true);

        fixture.service.recoverExpiredWaitingRuns();

        ArgumentCaptor<AgentRun> captured = ArgumentCaptor.forClass(AgentRun.class);
        verify(fixture.runs).recoverExpiredWaitingLocationWithCas(captured.capture(), eq(waiting.version()));
        assertThat(captured.getValue().status()).isEqualTo(AgentRunStatus.RUNNING);
        verify(fixture.contexts).cleanupIfPresent("run-1");
        verify(fixture.supervisor).run(any());
        verify(fixture.results).record(captured.getValue(), (MultiToolSupervisorResult) null);
    }

    @Test
    void shouldNotResumeWhenWaitingRecoveryCasLosesRace() {
        Fixture fixture = fixture();
        AgentRun waiting = run(AgentRunStatus.WAITING_LOCATION);
        when(fixture.runs.findExpiredWaitingLocationByUser(USER_ID, 100)).thenReturn(List.of(waiting));
        when(fixture.runs.recoverExpiredWaitingLocationWithCas(any(), eq(waiting.version()))).thenReturn(false);
        when(fixture.runs.findByRunIdAndUserId("run-1", USER_ID)).thenReturn(Optional.of(waiting));

        fixture.service.recoverExpiredWaitingRuns();

        verify(fixture.supervisor, never()).run(any());
        verify(fixture.contexts, never()).cleanupIfPresent(any());
        verify(fixture.results, never()).record(any(), any(MultiToolSupervisorResult.class));
    }

    private static Fixture fixture() {
        CurrentUserAccessor users = Mockito.mock(CurrentUserAccessor.class);
        AgentDistanceRunInitializationService initialization = Mockito.mock(AgentDistanceRunInitializationService.class);
        AgentDistanceContextApplicationService contexts = Mockito.mock(AgentDistanceContextApplicationService.class);
        AgentRuntimeQueryService query = Mockito.mock(AgentRuntimeQueryService.class);
        AgentRunRepository runs = Mockito.mock(AgentRunRepository.class);
        MultiToolSupervisor supervisor = Mockito.mock(MultiToolSupervisor.class);
        AgentRunResultTransaction results = Mockito.mock(AgentRunResultTransaction.class);
        when(users.requireCurrentUserId()).thenReturn(USER_ID);
        AgentRuntimeQueryService.RuntimeView view = Mockito.mock(AgentRuntimeQueryService.RuntimeView.class);
        when(view.messages()).thenReturn(List.of(new AgentMessage(1L, "message-1", 1L, 100L, USER_ID,
                AgentMessageRole.USER, AgentMessageType.TEXT, "推荐电影", new AgentStoredJson("{}"),
                AgentMessageStatus.COMPLETED, LocalDateTime.of(2026, 8, 7, 12, 0),
                LocalDateTime.of(2026, 8, 7, 12, 0), LocalDateTime.of(2026, 8, 8, 12, 0))));
        when(query.queryMyRun("run-1")).thenReturn(view);
        return new Fixture(new AgentDistanceRecommendationApplicationService(
                users, initialization, contexts, query, runs, supervisor, results, CLOCK),
                runs, contexts, supervisor, results);
    }

    private static AgentRun run(AgentRunStatus status) {
        LocalDateTime now = LocalDateTime.of(2026, 8, 7, 12, 0);
        return new AgentRun(100L, "run-1", 1L, USER_ID, "request-1",
                new AgentRequestHash("v1", "a".repeat(64)), null, null, status, "trace-1", now,
                null, 0L, now, now, now.plusDays(30));
    }

    private record Fixture(AgentDistanceRecommendationApplicationService service, AgentRunRepository runs,
            AgentDistanceContextApplicationService contexts, MultiToolSupervisor supervisor,
            AgentRunResultTransaction results) {
    }
}
