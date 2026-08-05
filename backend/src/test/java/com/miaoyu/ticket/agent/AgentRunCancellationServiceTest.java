package com.miaoyu.ticket.agent;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.miaoyu.ticket.agent.application.AgentErrorCode;
import com.miaoyu.ticket.agent.application.persistence.AgentRunCancellationService;
import com.miaoyu.ticket.agent.application.persistence.AgentRunRepository;
import com.miaoyu.ticket.agent.application.persistence.AgentRunStepRepository;
import com.miaoyu.ticket.agent.application.persistence.AgentRuntimeEventService;
import com.miaoyu.ticket.agent.application.persistence.AgentSessionRepository;
import com.miaoyu.ticket.agent.domain.persistence.AgentEventType;
import com.miaoyu.ticket.agent.domain.persistence.AgentRequestHash;
import com.miaoyu.ticket.agent.domain.persistence.AgentRun;
import com.miaoyu.ticket.agent.domain.persistence.AgentRunStatus;
import com.miaoyu.ticket.agent.domain.persistence.AgentRunStep;
import com.miaoyu.ticket.agent.domain.persistence.AgentSession;
import com.miaoyu.ticket.agent.domain.persistence.AgentSessionStatus;
import com.miaoyu.ticket.agent.domain.persistence.AgentStoredJson;
import com.miaoyu.ticket.agent.domain.plan.FailurePolicy;
import com.miaoyu.ticket.agent.domain.plan.PlanNodeStatus;
import com.miaoyu.ticket.agent.domain.plan.PlanNodeType;
import com.miaoyu.ticket.auth.application.CurrentUserAccessor;
import com.miaoyu.ticket.common.error.BusinessException;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;

/** 取消必须只推进未开始节点，终态与重复请求只读取既有事实。 */
class AgentRunCancellationServiceTest {
    private static final long USER_ID = 7L;
    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-08-05T04:00:00Z"), ZoneId.of("Asia/Shanghai"));

    @Test
    void shouldCancelPendingStepsWithoutChangingRunningOrCompletedSteps() {
        AgentRunRepository runs = Mockito.mock(AgentRunRepository.class);
        AgentRunStepRepository steps = Mockito.mock(AgentRunStepRepository.class);
        AgentSessionRepository sessions = Mockito.mock(AgentSessionRepository.class);
        AgentRuntimeEventService events = Mockito.mock(AgentRuntimeEventService.class);
        AgentRun run = run(AgentRunStatus.RUNNING);
        when(runs.findByRunIdAndUserId("run-1", USER_ID)).thenReturn(Optional.of(run));
        when(steps.findByRunId(2L)).thenReturn(List.of(step("done", PlanNodeStatus.SUCCESS),
                step("working", PlanNodeStatus.RUNNING), step("later", PlanNodeStatus.PENDING)));
        when(runs.updateTerminalWithCas(any(), eq(0L))).thenReturn(true);
        when(sessions.findByIdAndUserId(1L, USER_ID)).thenReturn(Optional.of(session()));
        AgentRunCancellationService service = service(runs, steps, sessions, events);

        AgentRun result = service.cancelMyRun("run-1");

        assertThat(result.status()).isEqualTo(AgentRunStatus.CANCELLED);
        ArgumentCaptor<AgentRunStep> stepCaptor = ArgumentCaptor.forClass(AgentRunStep.class);
        verify(steps).updateWithCas(stepCaptor.capture(), eq(0L), eq(PlanNodeStatus.PENDING));
        assertThat(stepCaptor.getValue().status()).isEqualTo(PlanNodeStatus.SKIPPED);
        verify(events).append(eq(session()), any(), eq(AgentEventType.RUN_COMPLETE), any());
        verify(sessions).releaseActiveRun(1L, 2L);
    }

    @Test
    void shouldReturnTerminalRunWithoutWritingAgain() {
        AgentRunRepository runs = Mockito.mock(AgentRunRepository.class);
        AgentRunStepRepository steps = Mockito.mock(AgentRunStepRepository.class);
        AgentSessionRepository sessions = Mockito.mock(AgentSessionRepository.class);
        AgentRuntimeEventService events = Mockito.mock(AgentRuntimeEventService.class);
        AgentRun completed = run(AgentRunStatus.COMPLETED);
        when(runs.findByRunIdAndUserId("run-1", USER_ID)).thenReturn(Optional.of(completed));

        assertThat(service(runs, steps, sessions, events).cancelMyRun("run-1")).isEqualTo(completed);

        verify(steps, never()).findByRunId(anyLong());
        verify(runs, never()).updateTerminalWithCas(any(), anyLong());
        verify(events, never()).append(any(), any(), any(), any());
    }

    @Test
    void shouldReturnSavedCancelledRunForDuplicateCancelWithoutNewEvent() {
        AgentRunRepository runs = Mockito.mock(AgentRunRepository.class);
        AgentRunStepRepository steps = Mockito.mock(AgentRunStepRepository.class);
        AgentSessionRepository sessions = Mockito.mock(AgentSessionRepository.class);
        AgentRuntimeEventService events = Mockito.mock(AgentRuntimeEventService.class);
        AgentRun cancelled = run(AgentRunStatus.CANCELLED);
        when(runs.findByRunIdAndUserId("run-1", USER_ID)).thenReturn(Optional.of(cancelled));

        assertThat(service(runs, steps, sessions, events).cancelMyRun("run-1")).isEqualTo(cancelled);

        verify(steps, never()).findByRunId(anyLong());
        verify(runs, never()).updateTerminalWithCas(any(), anyLong());
        verify(events, never()).append(any(), any(), any(), any());
    }

    @Test
    void shouldHideOtherUsersRunAsNotFound() {
        AgentRunRepository runs = Mockito.mock(AgentRunRepository.class);
        AgentRunStepRepository steps = Mockito.mock(AgentRunStepRepository.class);
        AgentSessionRepository sessions = Mockito.mock(AgentSessionRepository.class);
        AgentRuntimeEventService events = Mockito.mock(AgentRuntimeEventService.class);
        when(runs.findByRunIdAndUserId("other", USER_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service(runs, steps, sessions, events).cancelMyRun("other"))
                .isInstanceOf(BusinessException.class)
                .extracting(exception -> ((BusinessException) exception).getErrorCode())
                .isEqualTo(AgentErrorCode.AGENT_RESOURCE_NOT_FOUND);
    }

    private static AgentRunCancellationService service(AgentRunRepository runs, AgentRunStepRepository steps,
            AgentSessionRepository sessions, AgentRuntimeEventService events) {
        CurrentUserAccessor currentUser = Mockito.mock(CurrentUserAccessor.class);
        when(currentUser.requireCurrentUserId()).thenReturn(USER_ID);
        return new AgentRunCancellationService(currentUser, runs, steps, sessions, events, CLOCK);
    }

    private static AgentRun run(AgentRunStatus status) {
        LocalDateTime now = LocalDateTime.of(2026, 8, 5, 12, 0);
        return new AgentRun(2L, "run-1", 1L, USER_ID, "request-1", new AgentRequestHash("v1", "a".repeat(64)),
                "plan-1", 1, status, "trace-1", now, status.isTerminal() ? now : null, 0L, now, now,
                now.plusDays(30));
    }

    private static AgentRunStep step(String nodeId, PlanNodeStatus status) {
        LocalDateTime now = LocalDateTime.of(2026, 8, 5, 12, 0);
        boolean pending = status == PlanNodeStatus.PENDING;
        return new AgentRunStep(3L, 2L, 1, nodeId, PlanNodeType.RENDER_RESULT, new AgentStoredJson("[]"),
                new AgentStoredJson("{}"), status, FailurePolicy.FAIL, pending ? 0 : 1, 0,
                status == PlanNodeStatus.RUNNING, false, null, null, new AgentStoredJson("{}"),
                pending ? null : now, pending || status == PlanNodeStatus.RUNNING ? null : now, 0L, now, now,
                now.plusDays(30));
    }

    private static AgentSession session() {
        LocalDateTime now = LocalDateTime.of(2026, 8, 5, 12, 0);
        return new AgentSession(1L, "session-1", USER_ID, null, AgentSessionStatus.ACTIVE, 2L, 0L,
                now, now, now.plusDays(30));
    }
}
