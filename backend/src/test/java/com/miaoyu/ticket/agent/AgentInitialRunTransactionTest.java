package com.miaoyu.ticket.agent;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.when;

import com.miaoyu.ticket.agent.application.AgentErrorCode;
import com.miaoyu.ticket.agent.application.persistence.AgentInitialRunTransaction;
import com.miaoyu.ticket.agent.application.persistence.AgentMessageRepository;
import com.miaoyu.ticket.agent.application.persistence.AgentMessageSubmissionCommand;
import com.miaoyu.ticket.agent.application.persistence.AgentRequestHashFactory;
import com.miaoyu.ticket.agent.application.persistence.AgentRunRepository;
import com.miaoyu.ticket.agent.application.persistence.AgentRuntimeEventService;
import com.miaoyu.ticket.agent.application.persistence.AgentSessionRepository;
import com.miaoyu.ticket.agent.domain.persistence.AgentSession;
import com.miaoyu.ticket.agent.domain.persistence.AgentSessionStatus;
import com.miaoyu.ticket.agent.domain.plan.SlotSnapshot;
import com.miaoyu.ticket.agent.domain.plan.PlanValidationContext;
import com.miaoyu.ticket.common.error.BusinessException;
import com.miaoyu.ticket.common.id.BusinessIdGenerator;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;
import org.mockito.Mockito;
import org.springframework.dao.DuplicateKeyException;

/** 初始短事务必须先写运行和用户消息，再条件占用会话。 */
class AgentInitialRunTransactionTest {

    @Test
    void shouldInsertRunAndMessageBeforeClaimingSession() {
        Fixture fixture = fixture(true);

        var result = fixture.transaction().submit(7L, command());

        assertEquals(100L, result.run().id());
        assertEquals(false, result.reused());
        InOrder order = inOrder(fixture.runRepository(), fixture.messageRepository(), fixture.sessionRepository());
        order.verify(fixture.runRepository()).insert(any());
        order.verify(fixture.messageRepository()).insert(any());
        order.verify(fixture.sessionRepository()).claimActiveRun(1L, 7L, 100L, NOW.plusDays(30));
    }

    @Test
    void shouldReturn206008WhenSessionAlreadyHasAnActiveRun() {
        Fixture fixture = fixture(false);

        BusinessException exception =
                assertThrows(BusinessException.class, () -> fixture.transaction().submit(7L, command()));

        assertEquals(AgentErrorCode.ACTIVE_RUN_CONFLICT, exception.getErrorCode());
        verify(fixture.sessionRepository()).claimActiveRun(1L, 7L, 100L, NOW.plusDays(30));
    }

    @Test
    void shouldReturnExistingRunWithoutCreatingAnotherOne() {
        Fixture fixture = fixture(true);
        var existing = new com.miaoyu.ticket.agent.domain.persistence.AgentRun(
                88L, "run-existing", 1L, 7L, "request-1",
                new AgentRequestHashFactory().create("推荐电影", new SlotSnapshot(1L, Map.of())), null, null,
                com.miaoyu.ticket.agent.domain.persistence.AgentRunStatus.RUNNING,
                "trace", NOW, null, 0L, NOW, NOW, NOW.plusDays(30));
        when(fixture.runRepository().findByClientRequestId(7L, 1L, "request-1")).thenReturn(Optional.of(existing));

        var result = fixture.transaction().submit(7L, command());

        assertEquals(existing, result.run());
        assertEquals(true, result.reused());
        verify(fixture.runRepository(), never()).insert(any());
    }

    @Test
    void shouldRejectReusedRequestIdWithDifferentHash() {
        Fixture fixture = fixture(true);
        var existing = new com.miaoyu.ticket.agent.domain.persistence.AgentRun(
                88L, "run-existing", 1L, 7L, "request-1",
                new AgentRequestHashFactory().create("旧内容", new SlotSnapshot(1L, Map.of())), null, null,
                com.miaoyu.ticket.agent.domain.persistence.AgentRunStatus.RUNNING,
                "trace", NOW, null, 0L, NOW, NOW, NOW.plusDays(30));
        when(fixture.runRepository().findByClientRequestId(7L, 1L, "request-1")).thenReturn(Optional.of(existing));

        BusinessException exception =
                assertThrows(BusinessException.class, () -> fixture.transaction().submit(7L, command()));

        assertEquals(AgentErrorCode.REQUEST_HASH_MISMATCH, exception.getErrorCode());
        verify(fixture.runRepository(), never()).insert(any());
    }

    @Test
    void shouldRollbackBeforeConcurrentWinnerIsReadInAnotherTransaction() {
        Fixture fixture = fixture(true);
        doThrow(new DuplicateKeyException("duplicate"))
                .when(fixture.runRepository())
                .insert(any());

        var exception = assertThrows(
                com.miaoyu.ticket.agent.application.persistence.AgentConcurrentDuplicateRequestException.class,
                () -> fixture.transaction().submit(7L, command()));

        assertEquals(new AgentRequestHashFactory().create("推荐电影", new SlotSnapshot(1L, Map.of())),
                exception.requestHash());
        verify(fixture.messageRepository(), never()).insert(any());
    }

    private static Fixture fixture(boolean claimResult) {
        AgentSessionRepository sessionRepository = Mockito.mock(AgentSessionRepository.class);
        AgentRunRepository runRepository = Mockito.mock(AgentRunRepository.class);
        AgentMessageRepository messageRepository = Mockito.mock(AgentMessageRepository.class);
        AgentRuntimeEventService runtimeEventService = Mockito.mock(AgentRuntimeEventService.class);
        when(sessionRepository.findBySessionIdAndUserId("session-1", 7L)).thenReturn(Optional.of(session()));
        when(runRepository.findByClientRequestId(7L, 1L, "request-1")).thenReturn(Optional.empty());
        when(sessionRepository.claimActiveRun(1L, 7L, 100L, NOW.plusDays(30))).thenReturn(claimResult);
        BusinessIdGenerator idGenerator = new BusinessIdGenerator() {
            private long next = 100L;

            @Override
            public long nextId() {
                return next++;
            }
        };
        AgentInitialRunTransaction transaction = new AgentInitialRunTransaction(
                sessionRepository,
                runRepository,
                messageRepository,
                new AgentRequestHashFactory(),
                runtimeEventService,
                idGenerator,
                Clock.fixed(Instant.parse("2026-08-04T02:00:00Z"), ZoneId.of("Asia/Shanghai")));
        return new Fixture(transaction, sessionRepository, runRepository, messageRepository);
    }

    private static AgentMessageSubmissionCommand command() {
        SlotSnapshot slotSnapshot = new SlotSnapshot(1L, Map.of());
        return new AgentMessageSubmissionCommand(
                "session-1",
                "推荐电影",
                "request-1",
                slotSnapshot,
                new PlanValidationContext(Map.of(), Map.of(), slotSnapshot),
                3000L);
    }

    private static AgentSession session() {
        return new AgentSession(
                1L, "session-1", 7L, null, AgentSessionStatus.ACTIVE, null, 0L, NOW, NOW, NOW.plusDays(30));
    }

    private record Fixture(
            AgentInitialRunTransaction transaction,
            AgentSessionRepository sessionRepository,
            AgentRunRepository runRepository,
            AgentMessageRepository messageRepository) {
    }
    private static final java.time.LocalDateTime NOW = java.time.LocalDateTime.of(2026, 8, 4, 10, 0);
}
