package com.miaoyu.ticket.agent.infrastructure.confirmation;

import com.miaoyu.ticket.agent.application.confirmation.AgentConfirmationFactsProvider;
import com.miaoyu.ticket.agent.application.persistence.AgentRunRepository;
import com.miaoyu.ticket.agent.application.persistence.AgentRunStepRepository;
import com.miaoyu.ticket.agent.domain.confirmation.AgentConfirmationAction;
import com.miaoyu.ticket.agent.domain.confirmation.AgentConfirmationValidationContext;
import com.miaoyu.ticket.agent.domain.persistence.AgentRun;
import com.miaoyu.ticket.agent.domain.persistence.AgentRunStep;
import com.miaoyu.ticket.agent.domain.plan.PlanNodeStatus;
import com.miaoyu.ticket.agent.domain.persistence.AgentRunStatus;
import com.miaoyu.ticket.order.api.CreateOrderPrecheckCommand;
import com.miaoyu.ticket.order.api.CreateOrderTool;
import java.time.Clock;
import java.time.LocalDateTime;
import org.springframework.stereotype.Component;

/** 重新读取 B 的运行事实并调用 A 的只读预检；不直接访问订单内部层。 */
@Component
public class PersistentAgentConfirmationFactsProvider implements AgentConfirmationFactsProvider {
    private final AgentRunRepository runRepository;
    private final AgentRunStepRepository stepRepository;
    private final CreateOrderTool createOrderTool;
    private final Clock clock;

    public PersistentAgentConfirmationFactsProvider(
            AgentRunRepository runRepository,
            AgentRunStepRepository stepRepository,
            CreateOrderTool createOrderTool,
            Clock clock) {
        this.runRepository = runRepository;
        this.stepRepository = stepRepository;
        this.createOrderTool = createOrderTool;
        this.clock = clock;
    }

    @Override
    public AgentConfirmationValidationContext load(AgentConfirmationAction action, long currentUserId) {
        if (action.userId() != currentUserId) {
            return new AgentConfirmationValidationContext(
                    currentUserId, com.miaoyu.ticket.agent.domain.persistence.AgentRunStatus.FAILED,
                    action.planId(), action.planVersion(), PlanNodeStatus.WAITING_CONFIRMATION,
                    action.parameterHash(), false, LocalDateTime.now(clock));
        }
        AgentRun run = runRepository.findByRunIdAndUserId(action.runId(), currentUserId).orElse(null);
        if (action.status() != com.miaoyu.ticket.agent.domain.confirmation.AgentConfirmationActionStatus
                        .PENDING_CONFIRMATION
                || run == null
                || run.status() != AgentRunStatus.RUNNING) {
            return context(action, currentUserId, run, false);
        }
        if (run.planId() == null
                || run.planVersion() == null
                || !action.planId().equals(run.planId())
                || action.planVersion() != run.planVersion()) {
            return context(action, currentUserId, run, false);
        }
        CreateOrderPrecheckCommand precheck =
                new CreateOrderPrecheckCommand(action.command().showId(), action.command().sortedSeatIds());
        if (nodeStatus(action, run) != PlanNodeStatus.WAITING_CONFIRMATION) {
            return context(action, currentUserId, run, false);
        }
        boolean executable = createOrderTool.validate(precheck).executable();
        return context(action, currentUserId, run, executable);
    }

    private AgentConfirmationValidationContext context(
            AgentConfirmationAction action, long currentUserId, AgentRun run, boolean businessDataValid) {
        return new AgentConfirmationValidationContext(
                currentUserId,
                run == null ? AgentRunStatus.FAILED : run.status(),
                run == null || run.planId() == null ? action.planId() : run.planId(),
                run == null || run.planVersion() == null ? action.planVersion() : run.planVersion(),
                run == null ? PlanNodeStatus.FAILED : nodeStatus(action, run),
                action.parameterHash(), businessDataValid, LocalDateTime.now(clock));
    }

    private PlanNodeStatus nodeStatus(AgentConfirmationAction action, AgentRun run) {
        return stepRepository.findByRunId(run.id()).stream()
                .filter(step -> step.planVersion() == action.planVersion())
                .filter(step -> step.nodeId().equals(action.nodeId()))
                .map(AgentRunStep::status)
                .findFirst()
                .orElse(PlanNodeStatus.FAILED);
    }
}
