package com.miaoyu.ticket.agent;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.miaoyu.ticket.agent.application.persistence.AgentInitialRunResult;
import com.miaoyu.ticket.agent.application.persistence.AgentInitialRunTransaction;
import com.miaoyu.ticket.agent.application.persistence.AgentConcurrentRequestLookupTransaction;
import com.miaoyu.ticket.agent.application.persistence.AgentMessageRepository;
import com.miaoyu.ticket.agent.application.persistence.AgentMessageSubmissionCommand;
import com.miaoyu.ticket.agent.application.persistence.AgentMessageSubmissionService;
import com.miaoyu.ticket.agent.application.persistence.AgentPlanCardFollowUpResolver;
import com.miaoyu.ticket.agent.application.persistence.AgentReadOnlyExecutionTransaction;
import com.miaoyu.ticket.agent.application.persistence.AgentRunResultTransaction;
import com.miaoyu.ticket.agent.application.persistence.AgentRunStaleRecoveryService;
import com.miaoyu.ticket.agent.application.persistence.AgentRunStepRepository;
import com.miaoyu.ticket.agent.application.confirmation.CreateOrderConfirmationActionOrchestrator;
import com.miaoyu.ticket.agent.application.AgentDistanceRecommendationApplicationService;
import com.miaoyu.ticket.agent.application.model.ReplyGenerationResponse;
import com.miaoyu.ticket.agent.application.reply.AgentReplyMessageType;
import com.miaoyu.ticket.agent.application.reply.TextReplyFacts;
import com.miaoyu.ticket.agent.application.run.MultiToolSupervisorRequest;
import com.miaoyu.ticket.agent.application.run.MultiToolSupervisorResult;
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
import java.util.Optional;
import org.junit.jupiter.api.Test;

/** 重复 clientRequestId 必须只读取已有事实，不能再次调用主控或 D 的只读工具。 */
class AgentMessageSubmissionServiceTest {

    @Test
    void shouldPassPersistedPlanExplanationToSupervisorWithoutReclassification() {
        CurrentUserAccessor currentUserAccessor = mock(CurrentUserAccessor.class);
        AgentInitialRunTransaction initialRunTransaction = mock(AgentInitialRunTransaction.class);
        AgentConcurrentRequestLookupTransaction concurrentRequestLookupTransaction =
                mock(AgentConcurrentRequestLookupTransaction.class);
        AgentRunResultTransaction runResultTransaction = mock(AgentRunResultTransaction.class);
        AgentReadOnlyExecutionTransaction readOnlyExecutionTransaction =
                mock(AgentReadOnlyExecutionTransaction.class);
        CreateOrderConfirmationActionOrchestrator confirmationActionOrchestrator =
                mock(CreateOrderConfirmationActionOrchestrator.class);
        AgentMessageRepository messageRepository = mock(AgentMessageRepository.class);
        AgentRunStepRepository stepRepository = mock(AgentRunStepRepository.class);
        AgentRunStaleRecoveryService staleRecoveryService = mock(AgentRunStaleRecoveryService.class);
        AgentDistanceRecommendationApplicationService distanceRecommendationService =
                mock(AgentDistanceRecommendationApplicationService.class);
        AgentPlanCardFollowUpResolver planCardFollowUpResolver = mock(AgentPlanCardFollowUpResolver.class);
        AgentRun running = run();
        AgentRun completed = completedRun();
        ReplyGenerationResponse explanation = new ReplyGenerationResponse(
                "第2个方案不需要继续调整。", AgentReplyMessageType.TEXT, new TextReplyFacts());
        MultiToolSupervisorResult supervisorResult = mock(MultiToolSupervisorResult.class);
        when(currentUserAccessor.requireCurrentUserId()).thenReturn(7L);
        when(initialRunTransaction.submit(7L, command())).thenReturn(new AgentInitialRunResult(running, false));
        when(planCardFollowUpResolver.resolve(1L, 7L, "推荐电影")).thenReturn(Optional.of(explanation));
        when(readOnlyExecutionTransaction.execute(
                org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any()))
                .thenReturn(supervisorResult);
        when(runResultTransaction.recordWithEvents(running, supervisorResult, false))
                .thenReturn(new AgentRunResultTransaction.RecordedRunResult(completed, List.of()));
        when(messageRepository.findByRunIdAndUserId(100L, 7L)).thenReturn(List.of());
        when(stepRepository.findByRunId(100L)).thenReturn(List.of());
        AgentMessageSubmissionService service = new AgentMessageSubmissionService(
                currentUserAccessor, initialRunTransaction, concurrentRequestLookupTransaction,
                runResultTransaction, readOnlyExecutionTransaction, confirmationActionOrchestrator,
                messageRepository, stepRepository, staleRecoveryService, distanceRecommendationService,
                planCardFollowUpResolver);

        service.submit(command());

        var request = org.mockito.ArgumentCaptor.forClass(MultiToolSupervisorRequest.class);
        verify(readOnlyExecutionTransaction).execute(request.capture(), org.mockito.ArgumentMatchers.any());
        assertEquals(explanation, request.getValue().trustedContextReply());
    }

    @Test
    void shouldPropagateCurrentUserSessionInvalidWithoutAgentSpecificMapping() {
        CurrentUserAccessor currentUserAccessor = mock(CurrentUserAccessor.class);
        AgentInitialRunTransaction initialRunTransaction = mock(AgentInitialRunTransaction.class);
        AgentConcurrentRequestLookupTransaction concurrentRequestLookupTransaction =
                mock(AgentConcurrentRequestLookupTransaction.class);
        AgentRunResultTransaction runResultTransaction = mock(AgentRunResultTransaction.class);
        AgentReadOnlyExecutionTransaction readOnlyExecutionTransaction =
                mock(AgentReadOnlyExecutionTransaction.class);
        CreateOrderConfirmationActionOrchestrator confirmationActionOrchestrator =
                mock(CreateOrderConfirmationActionOrchestrator.class);
        AgentMessageRepository messageRepository = mock(AgentMessageRepository.class);
        AgentRunStepRepository stepRepository = mock(AgentRunStepRepository.class);
        AgentRunStaleRecoveryService staleRecoveryService = mock(AgentRunStaleRecoveryService.class);
        AgentDistanceRecommendationApplicationService distanceRecommendationService =
                mock(AgentDistanceRecommendationApplicationService.class);
        AgentPlanCardFollowUpResolver planCardFollowUpResolver = mock(AgentPlanCardFollowUpResolver.class);
        when(currentUserAccessor.requireCurrentUserId())
                .thenThrow(new BusinessException(AuthErrorCode.SESSION_INVALID));
        AgentMessageSubmissionService service = new AgentMessageSubmissionService(
                currentUserAccessor,
                initialRunTransaction,
                concurrentRequestLookupTransaction,
                runResultTransaction,
                readOnlyExecutionTransaction,
                confirmationActionOrchestrator,
                messageRepository,
                stepRepository,
                staleRecoveryService,
                distanceRecommendationService,
                planCardFollowUpResolver);

        BusinessException exception = org.junit.jupiter.api.Assertions.assertThrows(
                BusinessException.class, () -> service.submit(command()));

        assertEquals(AuthErrorCode.SESSION_INVALID, exception.getErrorCode());
        verify(initialRunTransaction, never()).submit(
                org.mockito.ArgumentMatchers.anyLong(), org.mockito.ArgumentMatchers.any());
        verify(staleRecoveryService, never()).recoverStaleRuns();
        verify(distanceRecommendationService, never()).recoverExpiredWaitingRuns();
    }

    @Test
    void shouldReturnPersistedSnapshotWithoutCallingAgentForReusedRequest() {
        CurrentUserAccessor currentUserAccessor = mock(CurrentUserAccessor.class);
        AgentInitialRunTransaction initialRunTransaction = mock(AgentInitialRunTransaction.class);
        AgentConcurrentRequestLookupTransaction concurrentRequestLookupTransaction =
                mock(AgentConcurrentRequestLookupTransaction.class);
        AgentRunResultTransaction runResultTransaction = mock(AgentRunResultTransaction.class);
        AgentReadOnlyExecutionTransaction readOnlyExecutionTransaction =
                mock(AgentReadOnlyExecutionTransaction.class);
        CreateOrderConfirmationActionOrchestrator confirmationActionOrchestrator =
                mock(CreateOrderConfirmationActionOrchestrator.class);
        AgentMessageRepository messageRepository = mock(AgentMessageRepository.class);
        AgentRunStepRepository stepRepository = mock(AgentRunStepRepository.class);
        AgentRunStaleRecoveryService staleRecoveryService = mock(AgentRunStaleRecoveryService.class);
        AgentDistanceRecommendationApplicationService distanceRecommendationService =
                mock(AgentDistanceRecommendationApplicationService.class);
        AgentPlanCardFollowUpResolver planCardFollowUpResolver = mock(AgentPlanCardFollowUpResolver.class);
        AgentRun run = run();
        when(currentUserAccessor.requireCurrentUserId()).thenReturn(7L);
        when(initialRunTransaction.submit(7L, command())).thenReturn(new AgentInitialRunResult(run, true));
        when(messageRepository.findByRunIdAndUserId(100L, 7L)).thenReturn(List.of());
        when(stepRepository.findByRunId(100L)).thenReturn(List.of());
        AgentMessageSubmissionService service = new AgentMessageSubmissionService(
                currentUserAccessor,
                initialRunTransaction,
                concurrentRequestLookupTransaction,
                runResultTransaction,
                readOnlyExecutionTransaction,
                confirmationActionOrchestrator,
                messageRepository,
                stepRepository,
                staleRecoveryService,
                distanceRecommendationService,
                planCardFollowUpResolver);

        var result = service.submit(command());

        assertTrue(result.reused());
        verify(staleRecoveryService).recoverStaleRuns();
        verify(distanceRecommendationService).recoverExpiredWaitingRuns();
        verify(messageRepository).findByRunIdAndUserId(100L, 7L);
        verify(readOnlyExecutionTransaction, never()).execute(
                org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any());
        verify(runResultTransaction, never()).record(
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any(
                        com.miaoyu.ticket.agent.application.run.MultiToolSupervisorResult.class));
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

    private static AgentRun completedRun() {
        AgentRun running = run();
        return new AgentRun(
                running.id(), running.runId(), running.sessionId(), running.userId(), running.clientRequestId(),
                running.requestHash(), null, null, AgentRunStatus.COMPLETED, running.traceId(), running.startedAt(),
                running.startedAt().plusSeconds(1L), 1L, running.createTime(), running.updateTime().plusSeconds(1L),
                running.expireAt());
    }
}
