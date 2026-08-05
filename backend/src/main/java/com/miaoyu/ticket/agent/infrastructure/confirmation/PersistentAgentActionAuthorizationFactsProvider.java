package com.miaoyu.ticket.agent.infrastructure.confirmation;

import com.miaoyu.ticket.agent.application.confirmation.AgentActionAuthorizationFacts;
import com.miaoyu.ticket.agent.application.confirmation.AgentActionAuthorizationFactsProvider;
import com.miaoyu.ticket.agent.application.persistence.AgentRunRepository;
import com.miaoyu.ticket.agent.domain.confirmation.AgentConfirmationAction;
import com.miaoyu.ticket.agent.domain.persistence.AgentRun;
import com.miaoyu.ticket.agent.domain.persistence.AgentRunStatus;
import java.time.Clock;
import java.time.LocalDateTime;
import org.springframework.stereotype.Component;

/** A 调用公开授权 Port 时重新读取 B 的运行和计划事实，不访问票务持久化。 */
@Component
public class PersistentAgentActionAuthorizationFactsProvider implements AgentActionAuthorizationFactsProvider {
    private final AgentRunRepository runRepository;
    private final Clock clock;

    public PersistentAgentActionAuthorizationFactsProvider(AgentRunRepository runRepository, Clock clock) {
        this.runRepository = runRepository;
        this.clock = clock;
    }

    @Override
    public AgentActionAuthorizationFacts load(AgentConfirmationAction action) {
        AgentRun run = runRepository.findByRunIdAndUserId(action.runId(), action.userId()).orElse(null);
        boolean executable = run != null
                && run.status() == AgentRunStatus.RUNNING
                && !action.isExpiredAt(LocalDateTime.now(clock));
        return new AgentActionAuthorizationFacts(
                run == null ? action.runId() : run.runId(),
                run == null || run.planId() == null ? action.planId() : run.planId(),
                run == null || run.planVersion() == null ? action.planVersion() : run.planVersion(),
                action.nodeId(), action.command().toolName(), action.parameterHash(), executable);
    }
}
