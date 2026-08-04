package com.miaoyu.ticket.agent;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.miaoyu.ticket.agent.application.persistence.AgentMessageRepository;
import com.miaoyu.ticket.agent.application.persistence.AgentRunRepository;
import com.miaoyu.ticket.agent.application.persistence.AgentRunStaleRecoveryService;
import com.miaoyu.ticket.agent.application.persistence.AgentRunStaleRecoveryTransaction;
import com.miaoyu.ticket.agent.application.persistence.AgentRunStepRepository;
import com.miaoyu.ticket.agent.application.persistence.AgentSessionRepository;
import com.miaoyu.ticket.agent.application.persistence.AgentStaleRecoveryConcurrentException;
import com.miaoyu.ticket.agent.domain.persistence.AgentRequestHash;
import com.miaoyu.ticket.agent.domain.persistence.AgentRun;
import com.miaoyu.ticket.agent.domain.persistence.AgentRunStatus;
import com.miaoyu.ticket.agent.domain.persistence.AgentRunStep;
import com.miaoyu.ticket.agent.domain.persistence.AgentStoredJson;
import com.miaoyu.ticket.agent.domain.plan.FailurePolicy;
import com.miaoyu.ticket.agent.domain.plan.PlanNodeStatus;
import com.miaoyu.ticket.agent.domain.plan.PlanNodeType;
import com.miaoyu.ticket.common.id.BusinessIdGenerator;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;

/** 陈旧恢复只处理超过 30 秒的运行，并以 CAS 结束步骤和会话占用。 */
class AgentRunStaleRecoveryTest {

    @Test
    void shouldScanRunningRecordsAtThirtySecondBoundary() {
        AgentRunRepository runRepository = Mockito.mock(AgentRunRepository.class);
        AgentRunStaleRecoveryTransaction recoveryTransaction = Mockito.mock(AgentRunStaleRecoveryTransaction.class);
        Clock clock = fixedClock();
        AgentRunStaleRecoveryService service =
                new AgentRunStaleRecoveryService(runRepository, recoveryTransaction, clock);
        when(runRepository.findStaleRunningBefore(LocalDateTime.of(2026, 8, 4, 9, 59, 30), 100))
                .thenReturn(List.of(run()));

        service.recoverStaleRuns();

        verify(recoveryTransaction).recover(run());
    }

    @Test
    void shouldFailPendingAndRunningStepsThenReleaseCurrentSessionRun() {
        Fixture fixture = fixture();
        when(fixture.stepRepository().findByRunId(100L)).thenReturn(List.of(runningStep()));
        when(fixture.stepRepository().updateWithCas(any(), eq(0L), eq(PlanNodeStatus.RUNNING))).thenReturn(true);
        when(fixture.runRepository().updateTerminalWithCas(any(), eq(0L))).thenReturn(true);

        fixture.transaction().recover(run());

        ArgumentCaptor<AgentRunStep> step = ArgumentCaptor.forClass(AgentRunStep.class);
        verify(fixture.stepRepository()).updateWithCas(step.capture(), eq(0L), eq(PlanNodeStatus.RUNNING));
        assertEquals(PlanNodeStatus.FAILED, step.getValue().status());
        assertEquals(false, step.getValue().recoveryPending());
        verify(fixture.sessionRepository()).releaseActiveRun(1L, 100L);
    }

    @Test
    void shouldAbortRecoveryWhenStepCasLosesRace() {
        Fixture fixture = fixture();
        when(fixture.stepRepository().findByRunId(100L)).thenReturn(List.of(runningStep()));
        when(fixture.stepRepository().updateWithCas(any(), eq(0L), eq(PlanNodeStatus.RUNNING))).thenReturn(false);

        assertThrows(AgentStaleRecoveryConcurrentException.class, () -> fixture.transaction().recover(run()));

        verify(fixture.runRepository(), never()).updateTerminalWithCas(any(), any(Long.class));
        verify(fixture.sessionRepository(), never()).releaseActiveRun(any(Long.class), any(Long.class));
    }

    private static AgentRunStep runningStep() {
        LocalDateTime startedAt = LocalDateTime.of(2026, 8, 4, 9, 58);
        return new AgentRunStep(
                200L, 100L, 1, "rank", PlanNodeType.CALL_TOOL, new AgentStoredJson("[]"),
                new AgentStoredJson("[]"), PlanNodeStatus.RUNNING, FailurePolicy.FAIL, 1, 0, true,
                false, null, null, new AgentStoredJson("{}"), startedAt, null, 0L, startedAt,
                startedAt, startedAt.plusDays(30));
    }

    private static AgentRun run() {
        LocalDateTime now = LocalDateTime.of(2026, 8, 4, 9, 0);
        return new AgentRun(
                100L, "run-1", 1L, 7L, "request-1", new AgentRequestHash("v1", "a".repeat(64)),
                "plan-1", 1, AgentRunStatus.RUNNING, "trace", now, null, 0L, now, now, now.plusDays(30));
    }

    private static Clock fixedClock() {
        return Clock.fixed(Instant.parse("2026-08-04T02:00:00Z"), ZoneId.of("Asia/Shanghai"));
    }

    private static Fixture fixture() {
        AgentRunRepository runRepository = Mockito.mock(AgentRunRepository.class);
        AgentRunStepRepository stepRepository = Mockito.mock(AgentRunStepRepository.class);
        AgentMessageRepository messageRepository = Mockito.mock(AgentMessageRepository.class);
        AgentSessionRepository sessionRepository = Mockito.mock(AgentSessionRepository.class);
        BusinessIdGenerator idGenerator = () -> 1000L;
        AgentRunStaleRecoveryTransaction transaction = new AgentRunStaleRecoveryTransaction(
                runRepository, stepRepository, messageRepository, sessionRepository, idGenerator, fixedClock());
        return new Fixture(transaction, runRepository, stepRepository, sessionRepository);
    }

    private record Fixture(
            AgentRunStaleRecoveryTransaction transaction,
            AgentRunRepository runRepository,
            AgentRunStepRepository stepRepository,
            AgentSessionRepository sessionRepository) {
    }
}
