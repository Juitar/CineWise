package com.miaoyu.ticket.agent.application.confirmation;

import com.miaoyu.ticket.agent.application.persistence.AgentRunStepRepository;
import com.miaoyu.ticket.agent.domain.confirmation.AgentConfirmationAction;
import com.miaoyu.ticket.agent.domain.persistence.AgentRun;
import com.miaoyu.ticket.agent.domain.plan.ExecutionPlanNode;
import com.miaoyu.ticket.agent.domain.plan.PlanNodeStatus;
import com.miaoyu.ticket.agent.domain.plan.PlanNodeType;
import java.util.Objects;

/** 在确认节点已落库后创建既有建单 action；不生成 actionId 或写标识。 */
public final class CreateOrderConfirmationActionOrchestrator {
    private final AgentRunStepRepository stepRepository;
    private final CreateOrderConfirmationCommandFactory commandFactory;
    private final AgentConfirmationActionCreationService actionCreationService;

    public CreateOrderConfirmationActionOrchestrator(
            AgentRunStepRepository stepRepository,
            CreateOrderConfirmationCommandFactory commandFactory,
            AgentConfirmationActionCreationService actionCreationService) {
        this.stepRepository = Objects.requireNonNull(stepRepository, "步骤仓储不能为空");
        this.commandFactory = Objects.requireNonNull(commandFactory, "确认命令转换器不能为空");
        this.actionCreationService = Objects.requireNonNull(actionCreationService, "确认动作服务不能为空");
    }

    public AgentConfirmationAction create(
            AgentRun run, ExecutionPlanNode confirmationNode, ExecutionPlanNode writeNode) {
        Objects.requireNonNull(run, "运行不能为空");
        if (run.planId() == null || run.planVersion() == null
                || confirmationNode.type() != PlanNodeType.CONFIRM_ACTION
                || !writeNode.dependsOn().contains(confirmationNode.nodeId())) {
            throw new IllegalArgumentException("确认节点与写节点不匹配");
        }
        boolean persistedWaiting = stepRepository.findByRunId(run.id()).stream().anyMatch(step ->
                step.planVersion() == run.planVersion()
                        && step.nodeId().equals(confirmationNode.nodeId())
                        && step.nodeType() == PlanNodeType.CONFIRM_ACTION
                        && step.status() == PlanNodeStatus.WAITING_CONFIRMATION);
        if (!persistedWaiting) {
            throw new IllegalStateException("确认节点尚未持久化为等待确认状态");
        }
        return actionCreationService.create(new CreateOrderConfirmationActionCommand(
                run.runId(), run.planId(), run.planVersion(), confirmationNode.nodeId(),
                commandFactory.create(writeNode)));
    }
}
