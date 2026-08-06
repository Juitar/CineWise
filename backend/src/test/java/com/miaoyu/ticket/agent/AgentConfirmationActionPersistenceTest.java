package com.miaoyu.ticket.agent;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.miaoyu.ticket.agent.domain.confirmation.AgentActionWriteIdentifiers;
import com.miaoyu.ticket.agent.domain.confirmation.AgentConfirmationAction;
import com.miaoyu.ticket.agent.domain.confirmation.AgentConfirmationActionStatus;
import com.miaoyu.ticket.agent.domain.confirmation.ConfirmedOrderCommand;
import com.miaoyu.ticket.agent.infrastructure.persistence.AgentConfirmationActionEntity;
import com.miaoyu.ticket.agent.infrastructure.persistence.AgentPersistenceMapper;
import com.miaoyu.ticket.agent.infrastructure.persistence.MybatisAgentConfirmationActionRepository;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

/** `agent_action` 的 JSON 快照和 CAS 只通过 B 自有 MyBatis Repository 访问。 */
class AgentConfirmationActionPersistenceTest {
    private static final LocalDateTime NOW = LocalDateTime.of(2026, 8, 5, 10, 0);

    @Test
    void shouldPersistAndRestoreUnknownActionWithOriginalWriteIdentifiers() {
        AgentPersistenceMapper mapper = mock(AgentPersistenceMapper.class);
        MybatisAgentConfirmationActionRepository repository = new MybatisAgentConfirmationActionRepository(
                mapper, new ObjectMapper());
        AgentConfirmationAction action = unknownAction();
        when(mapper.insertAction(any(AgentConfirmationActionEntity.class))).thenReturn(1);

        repository.insert(action);

        ArgumentCaptor<AgentConfirmationActionEntity> row =
                ArgumentCaptor.forClass(AgentConfirmationActionEntity.class);
        verify(mapper).insertAction(row.capture());
        assertEquals("action-1", row.getValue().actionId());
        assertTrue(row.getValue().commandSnapshot().contains("70001"));
        assertEquals(action.writeIdentifiers().idempotencyKey(), row.getValue().idempotencyKey());
        assertEquals(action.recoveryUntil(), row.getValue().recoveryUntil());

        when(mapper.findActionByActionId("action-1")).thenReturn(row.getValue());
        AgentConfirmationAction restored = repository.findByActionId("action-1").orElseThrow();
        assertEquals(action.command(), restored.command());
        assertEquals(action.parameterHash(), restored.parameterHash());
        assertEquals(action.recoveryUntil(), restored.recoveryUntil());
    }

    @Test
    void shouldUseVersionAndPriorStatusForCas() {
        AgentPersistenceMapper mapper = mock(AgentPersistenceMapper.class);
        MybatisAgentConfirmationActionRepository repository =
                new MybatisAgentConfirmationActionRepository(mapper, new ObjectMapper());
        AgentConfirmationAction current = unknownAction();
        AgentConfirmationAction next = current.markSucceeded("order-1", NOW.plusSeconds(3));
        when(mapper.updateActionWithCas(any(AgentConfirmationActionEntity.class), eq(current.version()),
                eq(AgentConfirmationActionStatus.RESULT_UNKNOWN.name()))).thenReturn(1);

        assertTrue(repository.compareAndSet(
                current.actionId(), current.version(), AgentConfirmationActionStatus.RESULT_UNKNOWN, next));
        verify(mapper).updateActionWithCas(any(AgentConfirmationActionEntity.class), eq(current.version()),
                eq(AgentConfirmationActionStatus.RESULT_UNKNOWN.name()));
    }

    private static AgentConfirmationAction unknownAction() {
        AgentConfirmationAction pending = AgentConfirmationAction.pending(
                1L, "action-1", 9L, 10L, 11L, "run-1", "plan-1", 2, "confirm-order",
                new ConfirmedOrderCommand("createOrder", "70001", List.of("2", "4")), NOW.plusMinutes(5), NOW);
        return pending.claim(AgentActionWriteIdentifiers.forAction("action-1"), NOW.plusSeconds(1))
                .markResultUnknown("结果确认中", NOW.plusSeconds(2));
    }
}
