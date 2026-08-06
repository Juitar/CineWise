package com.miaoyu.ticket.agent.application.persistence;

import com.miaoyu.ticket.agent.application.AgentFailurePersistedException;
import com.miaoyu.ticket.agent.application.confirmation.CreateOrderConfirmationActionOrchestrator;
import com.miaoyu.ticket.agent.application.run.MultiToolSupervisor;
import com.miaoyu.ticket.agent.application.run.MultiToolSupervisorRequest;
import com.miaoyu.ticket.agent.application.run.MultiToolSupervisorResult;
import com.miaoyu.ticket.agent.domain.persistence.AgentMessage;
import com.miaoyu.ticket.agent.domain.persistence.AgentRun;
import com.miaoyu.ticket.auth.application.CurrentUserAccessor;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import org.springframework.stereotype.Service;

/** 已校验计划的提交外层；主控调用刻意位于初始数据库短事务之外。 */
@Service
public class AgentMessageSubmissionService {
    private final CurrentUserAccessor currentUserAccessor;
    private final AgentInitialRunTransaction initialRunTransaction;
    private final AgentConcurrentRequestLookupTransaction concurrentRequestLookupTransaction;
    private final AgentRunResultTransaction runResultTransaction;
    private final MultiToolSupervisor multiToolSupervisor;
    private final CreateOrderConfirmationActionOrchestrator confirmationActionOrchestrator;
    private final AgentMessageRepository messageRepository;
    private final AgentRunStepRepository stepRepository;
    private final AgentRunStaleRecoveryService staleRecoveryService;

    public AgentMessageSubmissionService(
            CurrentUserAccessor currentUserAccessor,
            AgentInitialRunTransaction initialRunTransaction,
            AgentConcurrentRequestLookupTransaction concurrentRequestLookupTransaction,
            AgentRunResultTransaction runResultTransaction,
            MultiToolSupervisor multiToolSupervisor,
            CreateOrderConfirmationActionOrchestrator confirmationActionOrchestrator,
            AgentMessageRepository messageRepository,
            AgentRunStepRepository stepRepository,
            AgentRunStaleRecoveryService staleRecoveryService) {
        this.currentUserAccessor = currentUserAccessor;
        this.initialRunTransaction = initialRunTransaction;
        this.concurrentRequestLookupTransaction = concurrentRequestLookupTransaction;
        this.runResultTransaction = runResultTransaction;
        this.multiToolSupervisor = multiToolSupervisor;
        this.confirmationActionOrchestrator = confirmationActionOrchestrator;
        this.messageRepository = messageRepository;
        this.stepRepository = stepRepository;
        this.staleRecoveryService = staleRecoveryService;
    }

    /** 创建持久化运行后再调用模型和只读工具；不在此方法上声明事务。 */
    public AgentMessageSubmissionResult submit(AgentMessageSubmissionCommand command) {
        AgentMessageSubmissionCommand request = Objects.requireNonNull(command, "提交命令不能为空");
        long userId = currentUserAccessor.requireCurrentUserId();
        staleRecoveryService.recoverStaleRuns();
        AgentInitialRunResult initial;
        try {
            initial = initialRunTransaction.submit(userId, request);
        } catch (AgentConcurrentDuplicateRequestException exception) {
            AgentRun winner = concurrentRequestLookupTransaction.findWinner(userId, request, exception.requestHash());
            return new AgentMessageSubmissionResult(snapshot(winner, userId), true);
        }
        if (initial.reused()) {
            return new AgentMessageSubmissionResult(snapshot(initial.run(), userId), true);
        }
        try {
            MultiToolSupervisorResult result = multiToolSupervisor.run(new MultiToolSupervisorRequest(
                    request.clientRequestId(),
                    request.content(),
                    request.validationContext(),
                    initial.run().runId(),
                    initial.run().traceId(),
                    request.remainingDeadlineMs()));
            AgentRun recordedRun = runResultTransaction.record(initial.run(), result);
            createAwaitingOrderConfirmation(recordedRun, result);
            return new AgentMessageSubmissionResult(snapshot(recordedRun, userId), false);
        } catch (RuntimeException exception) {
            // 仅持久化稳定失败事实；异常原文不能进入 Agent 表。
            runResultTransaction.recordFailure(initial.run());
            throw new AgentFailurePersistedException();
        }
    }

    /**
     * 确认节点必须先由结果短事务持久化为 WAITING_CONFIRMATION，才允许复用 A 的公开服务创建 action。
     * 这里只选择已登记的 createOrder；不会生成 actionId 或写入幂等键。
     */
    private void createAwaitingOrderConfirmation(AgentRun run, MultiToolSupervisorResult result) {
        if (!result.awaitingConfirmation() || result.state() == null) {
            return;
        }
        result.state().plan().nodes().stream()
                .filter(node -> node.requiresConfirmation())
                .filter(node -> result.state().nodeState(node.nodeId()).status()
                        == com.miaoyu.ticket.agent.domain.plan.PlanNodeStatus.WAITING_CONFIRMATION)
                .forEach(confirmationNode -> result.state().plan().nodes().stream()
                        .filter(node -> com.miaoyu.ticket.order.api.CreateOrderTool.TARGET_NAME
                                .equals(node.targetName()))
                        .filter(node -> node.dependsOn().contains(confirmationNode.nodeId()))
                        .forEach(writeNode -> confirmationActionOrchestrator.create(run, confirmationNode, writeNode)));
    }

    private AgentPersistedRunSnapshot snapshot(AgentRun run, long userId) {
        List<AgentMessage> messages = messageRepository.findByRunIdAndUserId(run.id(), userId)
                .stream()
                .filter(message -> message.runId() == run.id())
                .sorted(Comparator.comparingLong(AgentMessage::id))
                .toList();
        return new AgentPersistedRunSnapshot(run, messages, stepRepository.findByRunId(run.id()));
    }
}
