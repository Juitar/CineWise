package com.miaoyu.ticket.agent;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.miaoyu.ticket.agent.application.AgentErrorCode;
import com.miaoyu.ticket.agent.application.confirmation.AgentConfirmationActionCreationService;
import com.miaoyu.ticket.agent.application.confirmation.AgentConfirmationActionRepository;
import com.miaoyu.ticket.agent.application.confirmation.AgentConfirmationEventPublisher;
import com.miaoyu.ticket.agent.application.confirmation.CreateOrderConfirmationActionCommand;
import com.miaoyu.ticket.agent.application.persistence.AgentRunRepository;
import com.miaoyu.ticket.agent.application.persistence.AgentSessionRepository;
import com.miaoyu.ticket.agent.domain.confirmation.AgentConfirmationAction;
import com.miaoyu.ticket.agent.domain.confirmation.ConfirmedOrderCommand;
import com.miaoyu.ticket.agent.domain.persistence.AgentRequestHash;
import com.miaoyu.ticket.agent.domain.persistence.AgentRun;
import com.miaoyu.ticket.agent.domain.persistence.AgentRunStatus;
import com.miaoyu.ticket.agent.domain.persistence.AgentSession;
import com.miaoyu.ticket.agent.domain.persistence.AgentSessionStatus;
import com.miaoyu.ticket.auth.application.CurrentUser;
import com.miaoyu.ticket.auth.application.CurrentUserAccessor;
import com.miaoyu.ticket.auth.application.RoleCode;
import com.miaoyu.ticket.common.error.BusinessException;
import com.miaoyu.ticket.common.id.BusinessIdGenerator;
import com.miaoyu.ticket.order.api.CreateOrderTool;
import com.miaoyu.ticket.order.api.OrderPrecheckResult;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;
import org.springframework.dao.DuplicateKeyException;

/** action 创建只能使用当前用户、已保存运行和服务端 Command，不能由请求体补全交易字段。 */
class AgentConfirmationActionCreationServiceTest {
    private static final LocalDateTime NOW = LocalDateTime.of(2026, 8, 5, 10, 0);

    private AgentConfirmationActionRepository actionRepository;
    private AgentRunRepository runRepository;
    private AgentSessionRepository sessionRepository;
    private AgentConfirmationEventPublisher eventPublisher;
    private CreateOrderTool createOrderTool;
    private BusinessIdGenerator idGenerator;
    private AgentConfirmationActionCreationService service;

    @BeforeEach
    void setUp() {
        actionRepository = Mockito.mock(AgentConfirmationActionRepository.class);
        runRepository = Mockito.mock(AgentRunRepository.class);
        sessionRepository = Mockito.mock(AgentSessionRepository.class);
        eventPublisher = Mockito.mock(AgentConfirmationEventPublisher.class);
        createOrderTool = Mockito.mock(CreateOrderTool.class);
        idGenerator = Mockito.mock(BusinessIdGenerator.class);
        CurrentUserAccessor currentUser = () -> new CurrentUser(9L, RoleCode.USER, 1L);
        service = new AgentConfirmationActionCreationService(actionRepository, runRepository, sessionRepository,
                eventPublisher, createOrderTool, currentUser, idGenerator,
                Clock.fixed(Instant.parse("2026-08-05T02:00:00Z"), ZoneId.of("Asia/Shanghai")));
        when(runRepository.findByRunIdAndUserId("run-1", 9L)).thenReturn(Optional.of(run()));
        when(sessionRepository.findByIdAndUserId(10L, 9L)).thenReturn(Optional.of(session()));
        when(createOrderTool.validate(any())).thenReturn(OrderPrecheckResult.allowed());
        when(actionRepository.findByCreationKey(Mockito.anyLong(), Mockito.anyLong(), Mockito.anyString(),
                Mockito.anyInt(), Mockito.anyString(), Mockito.anyString(), Mockito.anyString()))
                .thenReturn(Optional.empty());
        when(idGenerator.nextId()).thenReturn(123L);
    }

    @Test
    void shouldCreateOneServerBoundActionAndPublishItsCardAfterInsert() {
        ArgumentCaptor<AgentConfirmationAction> inserted = ArgumentCaptor.forClass(AgentConfirmationAction.class);

        AgentConfirmationAction created = service.create(command());

        verify(actionRepository).insert(inserted.capture());
        verify(eventPublisher).publish(created);
        assertThat(created).isEqualTo(inserted.getValue());
        assertThat(created.userId()).isEqualTo(9L);
        assertThat(created.agentSessionId()).isEqualTo(10L);
        assertThat(created.agentRunId()).isEqualTo(11L);
        assertThat(created.planId()).isEqualTo("plan-1");
        assertThat(created.command().sortedSeatIds()).containsExactly("2", "4");
        assertThat(created.expireAt()).isEqualTo(NOW.plusMinutes(10));
        assertThat(created.writeIdentifiers()).isNull();
        verify(createOrderTool).validate(any());
    }

    @Test
    void shouldReuseTheExistingActionWithoutPublishingOrCreatingNewKeys() {
        AgentConfirmationAction existing = AgentConfirmationAction.pending(
                91L, "existing-action", 9L, 10L, 11L, "run-1", "plan-1", 2, "confirm-order",
                command().command(), NOW.plusMinutes(5), NOW);
        when(actionRepository.findByCreationKey(Mockito.anyLong(), Mockito.anyLong(), Mockito.anyString(),
                Mockito.anyInt(), Mockito.anyString(), Mockito.anyString(), Mockito.anyString()))
                .thenReturn(Optional.of(existing));

        AgentConfirmationAction reused = service.create(command());

        assertThat(reused).isSameAs(existing);
        verify(actionRepository, never()).insert(any());
        verify(eventPublisher, never()).publish(any());
        verify(idGenerator, never()).nextId();
    }

    @Test
    void shouldRejectInvalidBusinessSelectionBeforeCreatingAnAction() {
        when(createOrderTool.validate(any())).thenReturn(OrderPrecheckResult.rejected(204001));

        assertThatThrownBy(() -> service.create(command()))
                .isInstanceOfSatisfying(BusinessException.class,
                        exception -> assertThat(exception.getErrorCode())
                                .isEqualTo(AgentErrorCode.ACTION_PARAMETER_CHANGED));

        verify(actionRepository, never()).insert(any());
        verify(eventPublisher, never()).publish(any());
    }

    @Test
    void shouldReturnTheCommittedCreationWinnerAfterTheUniqueKeyRejectsAConcurrentInsert() {
        AgentConfirmationAction winner = AgentConfirmationAction.pending(
                91L, "winner-action", 9L, 10L, 11L, "run-1", "plan-1", 2, "confirm-order",
                command().command(), NOW.plusMinutes(5), NOW);
        Mockito.doThrow(new DuplicateKeyException("duplicate action creation"))
                .when(actionRepository).insert(any());
        when(actionRepository.findByCreationKeyForUpdate(Mockito.anyLong(), Mockito.anyLong(), Mockito.anyString(),
                Mockito.anyInt(), Mockito.anyString(), Mockito.anyString(), Mockito.anyString()))
                .thenReturn(Optional.of(winner));

        assertThat(service.create(command())).isSameAs(winner);

        verify(eventPublisher, never()).publish(any());
    }

    private static CreateOrderConfirmationActionCommand command() {
        return new CreateOrderConfirmationActionCommand("run-1", "plan-1", 2, "confirm-order",
                new ConfirmedOrderCommand("createOrder", "70001", List.of("4", "2")));
    }

    private static AgentRun run() {
        return new AgentRun(11L, "run-1", 10L, 9L, "request-1", new AgentRequestHash("v1", "a".repeat(64)),
                "plan-1", 2, AgentRunStatus.RUNNING, "trace-1", NOW, null, 0L, NOW, NOW, NOW.plusDays(30));
    }

    private static AgentSession session() {
        return new AgentSession(10L, "session-1", 9L, null, AgentSessionStatus.ACTIVE, 11L,
                0L, NOW, NOW, NOW.plusDays(30));
    }
}
