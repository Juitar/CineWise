package com.miaoyu.ticket.agent;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.miaoyu.ticket.agent.application.confirmation.AgentActionAuthorizationDeniedException;
import com.miaoyu.ticket.agent.application.confirmation.AgentActionAuthorizationFacts;
import com.miaoyu.ticket.agent.application.confirmation.AgentActionAuthorizationFactsProvider;
import com.miaoyu.ticket.agent.application.confirmation.AgentActionAuthorizationRequest;
import com.miaoyu.ticket.agent.application.confirmation.AgentActionAuthorizationService;
import com.miaoyu.ticket.agent.application.confirmation.AgentConfirmationActionRepository;
import com.miaoyu.ticket.agent.domain.confirmation.AgentActionParameterHash;
import com.miaoyu.ticket.agent.domain.confirmation.AgentActionWriteIdentifiers;
import com.miaoyu.ticket.agent.domain.confirmation.AgentConfirmationAction;
import com.miaoyu.ticket.agent.domain.confirmation.ConfirmedOrderCommand;
import com.miaoyu.ticket.agent.domain.tool.ToolContext;
import com.miaoyu.ticket.auth.application.CurrentUser;
import com.miaoyu.ticket.auth.application.CurrentUserAccessor;
import com.miaoyu.ticket.auth.application.RoleCode;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

/** 验证 A 调用前 B 的真实授权服务逐项拒绝不一致确认事实。 */
class AgentActionAuthorizationServiceTest {
    private static final String ACTION_ID = "action-authorization-1";
    private static final long OWNER_ID = 9L;
    private static final LocalDateTime NOW = LocalDateTime.of(2026, 8, 6, 10, 0);

    @Test
    void shouldAllowOnlyTheOwnerWithAllMatchingFacts() {
        AgentConfirmationAction action = executingAction();

        assertDoesNotThrow(() -> service(action, OWNER_ID, matchingFacts(action))
                .authorize(request(matchingContext(action))));
    }

    @Test
    void shouldRejectAnotherCurrentUser() {
        AgentConfirmationAction action = executingAction();

        assertRejected(service(action, OWNER_ID + 1, matchingFacts(action)), request(matchingContext(action)));
    }

    @Test
    void shouldRejectActionThatIsNotExecuting() {
        AgentConfirmationAction action = pendingAction();

        assertRejected(service(action, OWNER_ID, matchingFacts(action)), request(matchingContext(action)));
    }

    @Test
    void shouldRejectDifferentRunNodeOrToolContext() {
        AgentConfirmationAction action = executingAction();

        assertRejected(service(action, OWNER_ID, matchingFacts(action)), request(context(
                "another-run", action.nodeId(), action.command().toolName(), action)));
        assertRejected(service(action, OWNER_ID, matchingFacts(action)), request(context(
                action.runId(), "another-node", action.command().toolName(), action)));
        assertRejected(service(action, OWNER_ID, matchingFacts(action)), request(context(
                action.runId(), action.nodeId(), "another-tool", action)));
    }

    @Test
    void shouldRejectChangedPlanVersionOrCurrentParameterHash() {
        AgentConfirmationAction action = executingAction();

        assertRejected(service(action, OWNER_ID, new AgentActionAuthorizationFacts(
                action.runId(), action.planId(), action.planVersion() + 1, action.nodeId(),
                action.command().toolName(), action.parameterHash(), true)), request(matchingContext(action)));
        assertRejected(service(action, OWNER_ID, new AgentActionAuthorizationFacts(
                action.runId(), action.planId(), action.planVersion(), action.nodeId(),
                action.command().toolName(), AgentActionParameterHash.from(
                        new ConfirmedOrderCommand("createOrder", "70001", List.of("2", "5"))), true)),
                request(matchingContext(action)));
    }

    @Test
    void shouldRejectChangedCommandParameterHash() {
        AgentConfirmationAction action = executingAction();

        assertRejected(service(action, OWNER_ID, matchingFacts(action)), new AgentActionAuthorizationRequest(
                ACTION_ID, matchingContext(action), "70001", List.of("2", "5")));
    }

    @Test
    void shouldRejectDifferentClientRequestId() {
        AgentConfirmationAction action = executingAction();

        assertRejected(service(action, OWNER_ID, matchingFacts(action)), request(context(
                action.runId(), action.nodeId(), action.command().toolName(),
                "another-client-request", action.writeIdentifiers().idempotencyKey(), action)));
    }

    @Test
    void shouldRejectDifferentIdempotencyKey() {
        AgentConfirmationAction action = executingAction();

        assertRejected(service(action, OWNER_ID, matchingFacts(action)), request(context(
                action.runId(), action.nodeId(), action.command().toolName(),
                action.writeIdentifiers().clientRequestId(), "another-idempotency-key", action)));
    }

    private static AgentActionAuthorizationService service(
            AgentConfirmationAction action,
            long currentUserId,
            AgentActionAuthorizationFacts facts) {
        AgentConfirmationActionRepository repository = mock(AgentConfirmationActionRepository.class);
        when(repository.findByActionId(ACTION_ID)).thenReturn(Optional.of(action));
        AgentActionAuthorizationFactsProvider factsProvider = ignored -> facts;
        CurrentUserAccessor currentUserAccessor = () -> new CurrentUser(currentUserId, RoleCode.USER, 1L);
        return new AgentActionAuthorizationService(repository, factsProvider, currentUserAccessor);
    }

    private static void assertRejected(
            AgentActionAuthorizationService service,
            AgentActionAuthorizationRequest request) {
        assertThrows(AgentActionAuthorizationDeniedException.class, () -> service.authorize(request));
    }

    private static AgentConfirmationAction executingAction() {
        return pendingAction().claim(new AgentActionWriteIdentifiers(
                "agent-client-request", "agent-idempotency-key"), NOW.plusSeconds(1));
    }

    private static AgentConfirmationAction pendingAction() {
        return AgentConfirmationAction.pending(
                1L, ACTION_ID, OWNER_ID, 10L, 11L, "run-1", "plan-1", 2, "confirm-order",
                new ConfirmedOrderCommand("createOrder", "70001", List.of("2", "4")), NOW.plusMinutes(5), NOW);
    }

    private static AgentActionAuthorizationFacts matchingFacts(AgentConfirmationAction action) {
        return new AgentActionAuthorizationFacts(
                action.runId(), action.planId(), action.planVersion(), action.nodeId(),
                action.command().toolName(), action.parameterHash(), true);
    }

    private static AgentActionAuthorizationRequest request(ToolContext context) {
        return new AgentActionAuthorizationRequest(ACTION_ID, context, "70001", List.of("2", "4"));
    }

    private static ToolContext matchingContext(AgentConfirmationAction action) {
        return context(action.runId(), action.nodeId(), action.command().toolName(), action);
    }

    private static ToolContext context(
            String runId,
            String nodeId,
            String toolName,
            AgentConfirmationAction action) {
        String clientRequestId = action.writeIdentifiers() == null
                ? "pending-client-request"
                : action.writeIdentifiers().clientRequestId();
        String idempotencyKey = action.writeIdentifiers() == null
                ? "pending-idempotency-key"
                : action.writeIdentifiers().idempotencyKey();
        return context(runId, nodeId, toolName, clientRequestId, idempotencyKey, action);
    }

    private static ToolContext context(
            String runId,
            String nodeId,
            String toolName,
            String clientRequestId,
            String idempotencyKey,
            AgentConfirmationAction action) {
        return new ToolContext(runId, nodeId, toolName, List.of(), 3_000L, "trace-authorization",
                clientRequestId, idempotencyKey, action.version());
    }
}
