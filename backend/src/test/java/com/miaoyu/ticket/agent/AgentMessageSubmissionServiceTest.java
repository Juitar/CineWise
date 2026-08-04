package com.miaoyu.ticket.agent;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.miaoyu.ticket.agent.application.persistence.AgentInitialRunResult;
import com.miaoyu.ticket.agent.application.persistence.AgentInitialRunTransaction;
import com.miaoyu.ticket.agent.application.persistence.AgentMessageRepository;
import com.miaoyu.ticket.agent.application.persistence.AgentMessageSubmissionCommand;
import com.miaoyu.ticket.agent.application.persistence.AgentMessageSubmissionService;
import com.miaoyu.ticket.agent.application.persistence.AgentRunResultTransaction;
import com.miaoyu.ticket.agent.application.persistence.AgentRunStaleRecoveryService;
import com.miaoyu.ticket.agent.application.persistence.AgentRunStepRepository;
import com.miaoyu.ticket.agent.application.run.MinimalReadOnlyAgentService;
import com.miaoyu.ticket.agent.domain.persistence.AgentRequestHash;
import com.miaoyu.ticket.agent.domain.persistence.AgentRun;
import com.miaoyu.ticket.agent.domain.persistence.AgentRunStatus;
import com.miaoyu.ticket.agent.domain.plan.PlanValidationContext;
import com.miaoyu.ticket.agent.domain.plan.SlotSnapshot;
import com.miaoyu.ticket.auth.application.AuthErrorCode;
import com.miaoyu.ticket.auth.application.CurrentUserAccessor;
import com.miaoyu.ticket.common.error.BusinessException;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/** 重复 clientRequestId 必须只读取已有事实，不能再次调用主控或 D 的只读工具。 */
class AgentMessageSubmissionServiceTest {

    @Test
    void shouldPropagateCurrentUserSessionInvalidWithoutAgentSpecificMapping() {
        CurrentUserAccessor currentUserAccessor = mock(CurrentUserAccessor.class);
        AgentInitialRunTransaction initialRunTransaction = mock(AgentInitialRunTransaction.class);
        AgentRunResultTransaction runResultTransaction = mock(AgentRunResultTransaction.class);
        MinimalReadOnlyAgentService minimalReadOnlyAgentService = mock(MinimalReadOnlyAgentService.class);
        AgentMessageRepository messageRepository = mock(AgentMessageRepository.class);
        AgentRunStepRepository stepRepository = mock(AgentRunStepRepository.class);
        AgentRunStaleRecoveryService staleRecoveryService = mock(AgentRunStaleRecoveryService.class);
        when(currentUserAccessor.requireCurrentUserId())
                .thenThrow(new BusinessException(AuthErrorCode.SESSION_INVALID));
        AgentMessageSubmissionService service = new AgentMessageSubmissionService(
                currentUserAccessor,
                initialRunTransaction,
                runResultTransaction,
                minimalReadOnlyAgentService,
                messageRepository,
                stepRepository,
                staleRecoveryService);

        BusinessException exception = org.junit.jupiter.api.Assertions.assertThrows(
                BusinessException.class, () -> service.submit(command()));

        assertEquals(AuthErrorCode.SESSION_INVALID, exception.getErrorCode());
        verify(initialRunTransaction, never()).submit(
                org.mockito.ArgumentMatchers.anyLong(), org.mockito.ArgumentMatchers.any());
        verify(staleRecoveryService, never()).recoverStaleRuns();
    }

    @Test
    void shouldReturnPersistedSnapshotWithoutCallingAgentForReusedRequest() {
        CurrentUserAccessor currentUserAccessor = mock(CurrentUserAccessor.class);
        AgentInitialRunTransaction initialRunTransaction = mock(AgentInitialRunTransaction.class);
        AgentRunResultTransaction runResultTransaction = mock(AgentRunResultTransaction.class);
        MinimalReadOnlyAgentService minimalReadOnlyAgentService = mock(MinimalReadOnlyAgentService.class);
        AgentMessageRepository messageRepository = mock(AgentMessageRepository.class);
        AgentRunStepRepository stepRepository = mock(AgentRunStepRepository.class);
        AgentRunStaleRecoveryService staleRecoveryService = mock(AgentRunStaleRecoveryService.class);
        AgentRun run = run();
        when(currentUserAccessor.requireCurrentUserId()).thenReturn(7L);
        when(initialRunTransaction.submit(7L, command())).thenReturn(new AgentInitialRunResult(run, true));
        when(messageRepository.findBySessionIdAndUserId(1L, 7L, 100)).thenReturn(List.of());
        when(stepRepository.findByRunId(100L)).thenReturn(List.of());
        AgentMessageSubmissionService service = new AgentMessageSubmissionService(
                currentUserAccessor,
                initialRunTransaction,
                runResultTransaction,
                minimalReadOnlyAgentService,
                messageRepository,
                stepRepository,
                staleRecoveryService);

        var result = service.submit(command());

        assertTrue(result.reused());
        verify(staleRecoveryService).recoverStaleRuns();
        verify(minimalReadOnlyAgentService, never()).run(org.mockito.ArgumentMatchers.any());
        verify(runResultTransaction, never()).record(
                org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any());
    }

    private static AgentMessageSubmissionCommand command() {
        SlotSnapshot slots = new SlotSnapshot(1L, Map.of());
        return new AgentMessageSubmissionCommand(
                "session-1", "推荐电影", "request-1", slots,
                new PlanValidationContext(Map.of(), Map.of(), slots), 3000L);
    }

    private static AgentRun run() {
        LocalDateTime now = LocalDateTime.of(2026, 8, 4, 10, 0);
        return new AgentRun(
                100L, "run-1", 1L, 7L, "request-1", new AgentRequestHash("v1", "a".repeat(64)),
                null, null, AgentRunStatus.RUNNING, "trace", now, null, 0L, now, now, now.plusDays(30));
    }
}
