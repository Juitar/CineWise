package com.miaoyu.ticket.agent.application.confirmation;

import com.miaoyu.ticket.agent.domain.confirmation.AgentActionParameterHash;
import com.miaoyu.ticket.agent.domain.confirmation.AgentConfirmationAction;
import com.miaoyu.ticket.agent.domain.confirmation.AgentConfirmationActionStatus;
import com.miaoyu.ticket.auth.application.CurrentUserAccessor;
import java.util.Objects;
import org.springframework.stereotype.Service;

/** B 的公开授权实现，只校验 B 所拥有的确认、运行和计划事实。 */
@Service
public final class AgentActionAuthorizationService implements AgentActionAuthorizationPort {
    private final AgentConfirmationActionRepository repository;
    private final AgentActionAuthorizationFactsProvider factsProvider;
    private final CurrentUserAccessor currentUserAccessor;

    public AgentActionAuthorizationService(
            AgentConfirmationActionRepository repository,
            AgentActionAuthorizationFactsProvider factsProvider,
            CurrentUserAccessor currentUserAccessor) {
        this.repository = Objects.requireNonNull(repository, "repository 不能为空");
        this.factsProvider = Objects.requireNonNull(factsProvider, "factsProvider 不能为空");
        this.currentUserAccessor = Objects.requireNonNull(currentUserAccessor, "currentUserAccessor 不能为空");
    }

    @Override
    public void authorize(AgentActionAuthorizationRequest request) {
        Objects.requireNonNull(request, "request 不能为空");
        AgentConfirmationAction action = repository.findByActionId(request.actionId())
                .orElseThrow(AgentActionAuthorizationDeniedException::new);
        long currentUserId = currentUserAccessor.requireCurrentUserId();
        AgentActionAuthorizationFacts facts = factsProvider.load(action);
        AgentActionParameterHash commandHash = AgentActionParameterHash.from(request.toConfirmedOrderCommand());
        if (action.userId() != currentUserId
                || action.status() != AgentConfirmationActionStatus.EXECUTING
                || !action.runId().equals(request.context().runId())
                || !action.nodeId().equals(request.context().nodeId())
                || !action.command().toolName().equals(request.context().targetName())
                || !action.writeIdentifiers().clientRequestId().equals(request.context().clientRequestId())
                || !action.writeIdentifiers().idempotencyKey().equals(request.context().idempotencyKey())
                || !action.parameterHash().equals(commandHash)
                || !matchesCurrentFacts(action, facts)) {
            throw new AgentActionAuthorizationDeniedException();
        }
    }

    private static boolean matchesCurrentFacts(
            AgentConfirmationAction action,
            AgentActionAuthorizationFacts facts) {
        return facts != null
                && facts.executable()
                && action.runId().equals(facts.runId())
                && action.planId().equals(facts.planId())
                && action.planVersion() == facts.planVersion()
                && action.nodeId().equals(facts.nodeId())
                && action.command().toolName().equals(facts.toolName())
                && action.parameterHash().equals(facts.parameterHash());
    }
}
