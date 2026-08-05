package com.miaoyu.ticket.agent.application.persistence;

import com.miaoyu.ticket.agent.application.AgentFailurePersistedException;
import com.miaoyu.ticket.agent.application.run.MinimalReadOnlyAgentRequest;
import com.miaoyu.ticket.agent.application.run.MinimalReadOnlyAgentResult;
import com.miaoyu.ticket.agent.application.run.MinimalReadOnlyAgentService;
import com.miaoyu.ticket.agent.domain.persistence.AgentMessage;
import com.miaoyu.ticket.agent.domain.persistence.AgentRun;
import com.miaoyu.ticket.auth.application.CurrentUserAccessor;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import org.springframework.stereotype.Service;

/** 最小只读提交外层；主控调用刻意位于初始数据库短事务之外。 */
@Service
public class AgentMessageSubmissionService {
    private final CurrentUserAccessor currentUserAccessor;
    private final AgentInitialRunTransaction initialRunTransaction;
    private final AgentConcurrentRequestLookupTransaction concurrentRequestLookupTransaction;
    private final AgentRunResultTransaction runResultTransaction;
    private final MinimalReadOnlyAgentService minimalReadOnlyAgentService;
    private final AgentMessageRepository messageRepository;
    private final AgentRunStepRepository stepRepository;
    private final AgentRunStaleRecoveryService staleRecoveryService;

    public AgentMessageSubmissionService(
            CurrentUserAccessor currentUserAccessor,
            AgentInitialRunTransaction initialRunTransaction,
            AgentConcurrentRequestLookupTransaction concurrentRequestLookupTransaction,
            AgentRunResultTransaction runResultTransaction,
            MinimalReadOnlyAgentService minimalReadOnlyAgentService,
            AgentMessageRepository messageRepository,
            AgentRunStepRepository stepRepository,
            AgentRunStaleRecoveryService staleRecoveryService) {
        this.currentUserAccessor = currentUserAccessor;
        this.initialRunTransaction = initialRunTransaction;
        this.concurrentRequestLookupTransaction = concurrentRequestLookupTransaction;
        this.runResultTransaction = runResultTransaction;
        this.minimalReadOnlyAgentService = minimalReadOnlyAgentService;
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
            MinimalReadOnlyAgentResult result = minimalReadOnlyAgentService.run(new MinimalReadOnlyAgentRequest(
                    request.clientRequestId(),
                    request.content(),
                    request.validationContext(),
                    initial.run().runId(),
                    initial.run().traceId(),
                    request.remainingDeadlineMs()));
            AgentRun recordedRun = runResultTransaction.record(initial.run(), result);
            return new AgentMessageSubmissionResult(snapshot(recordedRun, userId), false);
        } catch (RuntimeException exception) {
            // 仅持久化稳定失败事实；异常原文不能进入 Agent 表。
            runResultTransaction.recordFailure(initial.run());
            throw new AgentFailurePersistedException();
        }
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
