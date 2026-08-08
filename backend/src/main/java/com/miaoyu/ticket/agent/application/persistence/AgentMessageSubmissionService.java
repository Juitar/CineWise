package com.miaoyu.ticket.agent.application.persistence;

import com.miaoyu.ticket.agent.application.AgentFailurePersistedException;
import com.miaoyu.ticket.agent.application.AgentDistanceRecommendationApplicationService;
import com.miaoyu.ticket.agent.application.confirmation.CreateOrderConfirmationActionOrchestrator;
import com.miaoyu.ticket.agent.application.run.MultiToolSupervisorRequest;
import com.miaoyu.ticket.agent.application.run.MultiToolSupervisorResult;
import com.miaoyu.ticket.agent.domain.persistence.AgentMessage;
import com.miaoyu.ticket.agent.domain.persistence.AgentRun;
import com.miaoyu.ticket.agent.domain.persistence.AgentRuntimeEvent;
import com.miaoyu.ticket.agent.domain.plan.PlanValidationContext;
import com.miaoyu.ticket.agent.domain.plan.SlotSnapshot;
import com.miaoyu.ticket.auth.application.CurrentUserAccessor;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/** 已校验计划的提交外层；主控调用刻意位于初始数据库短事务之外。 */
@Service
public class AgentMessageSubmissionService {
    private final CurrentUserAccessor currentUserAccessor;
    private final AgentInitialRunTransaction initialRunTransaction;
    private final AgentConcurrentRequestLookupTransaction concurrentRequestLookupTransaction;
    private final AgentRunResultTransaction runResultTransaction;
    private final AgentReadOnlyExecutionTransaction readOnlyExecutionTransaction;
    private final CreateOrderConfirmationActionOrchestrator confirmationActionOrchestrator;
    private final AgentMessageRepository messageRepository;
    private final AgentRunStepRepository stepRepository;
    private final AgentRunStaleRecoveryService staleRecoveryService;
    private final AgentDistanceRecommendationApplicationService distanceRecommendationService;
    private final AgentPlanCardFollowUpResolver planCardFollowUpResolver;

    public AgentMessageSubmissionService(
            CurrentUserAccessor currentUserAccessor,
            AgentInitialRunTransaction initialRunTransaction,
            AgentConcurrentRequestLookupTransaction concurrentRequestLookupTransaction,
            AgentRunResultTransaction runResultTransaction,
            AgentReadOnlyExecutionTransaction readOnlyExecutionTransaction,
            CreateOrderConfirmationActionOrchestrator confirmationActionOrchestrator,
            AgentMessageRepository messageRepository,
            AgentRunStepRepository stepRepository,
            AgentRunStaleRecoveryService staleRecoveryService,
            AgentDistanceRecommendationApplicationService distanceRecommendationService,
            AgentPlanCardFollowUpResolver planCardFollowUpResolver) {
        this.currentUserAccessor = currentUserAccessor;
        this.initialRunTransaction = initialRunTransaction;
        this.concurrentRequestLookupTransaction = concurrentRequestLookupTransaction;
        this.runResultTransaction = runResultTransaction;
        this.readOnlyExecutionTransaction = readOnlyExecutionTransaction;
        this.confirmationActionOrchestrator = confirmationActionOrchestrator;
        this.messageRepository = messageRepository;
        this.stepRepository = stepRepository;
        this.staleRecoveryService = staleRecoveryService;
        this.distanceRecommendationService = distanceRecommendationService;
        this.planCardFollowUpResolver = planCardFollowUpResolver;
    }

    /** 先挂起调用方事务，使初始运行、模型工具和结果落库分别使用独立短事务。 */
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public AgentMessageSubmissionResult submit(AgentMessageSubmissionCommand command) {
        AgentMessageSubmissionCommand request = Objects.requireNonNull(command, "提交命令不能为空");
        long userId = currentUserAccessor.requireCurrentUserId();
        recoverBeforeSubmission();
        AgentInitialRunResult initial;
        try {
            initial = initialRunTransaction.submit(userId, request);
        } catch (AgentConcurrentDuplicateRequestException exception) {
            AgentRun winner = concurrentRequestLookupTransaction.findWinner(userId, request, exception.requestHash());
            return new AgentMessageSubmissionResult(snapshot(winner, userId), true);
        }
        return execute(request, userId, initial);
    }

    /** 真实消息入口将幂等查询、槽位更新和 run 占用放入同一个会话锁事务。 */
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public AgentMessageSubmissionResult submitConversation(String sessionId, String content, String clientRequestId,
            String entry, long remainingDeadlineMs) {
        return completeConversation(beginConversation(sessionId, content, clientRequestId, entry, remainingDeadlineMs));
    }

    /**
     * 只完成会话锁定、槽位更新和初始运行短事务。调用方可以在模型调用前先把已提交的
     * {@code message.start} 事件推送给页面；不得在本方法中调用模型或工具。
     */
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public ConversationSubmission beginConversation(String sessionId, String content, String clientRequestId,
            String entry, long remainingDeadlineMs) {
        long userId = currentUserAccessor.requireCurrentUserId();
        recoverBeforeSubmission();
        AgentInitialRunResult initial = initialRunTransaction
                .submitConversation(userId, sessionId, content, clientRequestId, entry);
        SlotSnapshot slots = Objects.requireNonNull(initial.slotSnapshot(), "会话提交必须返回槽位快照");
        AgentMessageSubmissionCommand request = new AgentMessageSubmissionCommand(sessionId, content,
                clientRequestId, slots, new PlanValidationContext(slotTypes(slots), Map.of(), slots),
                remainingDeadlineMs);
        return new ConversationSubmission(request, userId, initial);
    }

    /**
     * 在初始短事务提交后执行模型和只读工具。重复请求只返回既有运行，绝不重复执行模型或工具。
     */
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public AgentMessageSubmissionResult completeConversation(ConversationSubmission submission) {
        return completeConversation(submission, ignored -> {
        });
    }

    /**
     * 普通文本分片先落为既有运行事件，再通知 SSE 调用方；回调失败不影响已提交的运行记录，
     * 断开连接的客户端可按事件游标恢复，绝不因此重跑模型或工具。
     */
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public AgentMessageSubmissionResult completeConversation(
            ConversationSubmission submission, Consumer<AgentRuntimeEvent> onTextDeltaRecorded) {
        ConversationSubmission value = Objects.requireNonNull(submission, "会话提交不能为空");
        Consumer<AgentRuntimeEvent> eventConsumer = Objects.requireNonNull(onTextDeltaRecorded, "文本事件回调不能为空");
        return execute(value.request(), value.userId(), value.initial(), eventConsumer);
    }

    private void recoverBeforeSubmission() {
        staleRecoveryService.recoverStaleRuns();
        // 普通消息入口也必须先恢复已过期的 WAITING_LOCATION，避免旧 active_run_id 阻塞新请求。
        distanceRecommendationService.recoverExpiredWaitingRuns();
    }

    private AgentMessageSubmissionResult execute(
            AgentMessageSubmissionCommand request, long userId, AgentInitialRunResult initial) {
        return execute(request, userId, initial, ignored -> {
        });
    }

    private AgentMessageSubmissionResult execute(
            AgentMessageSubmissionCommand request,
            long userId,
            AgentInitialRunResult initial,
            Consumer<AgentRuntimeEvent> onTextDeltaRecorded) {
        if (initial.reused()) {
            return new AgentMessageSubmissionResult(snapshot(initial.run(), userId), true);
        }
        try {
            AtomicBoolean textDeltaPersisted = new AtomicBoolean();
            var trustedContextReply = planCardFollowUpResolver
                    .resolve(initial.run().sessionId(), userId, request.content()).orElse(null);
            MultiToolSupervisorResult result = readOnlyExecutionTransaction.execute(new MultiToolSupervisorRequest(
                    request.clientRequestId(),
                    request.content(),
                    request.validationContext(),
                    initial.run().runId(),
                    initial.run().traceId(),
                    request.remainingDeadlineMs(), null, null,
                    initial.conversationContext().originalRequest(),
                    initial.conversationContext().inheritedIntent(), trustedContextReply), text -> {
                        AgentRuntimeEvent event = runResultTransaction.recordTextDelta(initial.run(), text);
                        textDeltaPersisted.set(true);
                        try {
                            onTextDeltaRecorded.accept(event);
                        } catch (RuntimeException ignored) {
                            // SSE 发送失败只影响当前连接；事件已保存，不能反向中断模型或重新执行本轮运行。
                        }
                    });
            AgentRunResultTransaction.RecordedRunResult recorded = runResultTransaction.recordWithEvents(
                    initial.run(), result,
                    textDeltaPersisted.get() && result.replyTextStreamed());
            recorded.completionEvents().forEach(event -> {
                try {
                    onTextDeltaRecorded.accept(event);
                } catch (RuntimeException ignored) {
                    // 终态事件已经落库；当前连接失败时仍可按游标恢复。
                }
            });
            AgentRun recordedRun = recorded.run();
            createAwaitingOrderConfirmation(recordedRun, result);
            return new AgentMessageSubmissionResult(snapshot(recordedRun, userId), false);
        } catch (RuntimeException exception) {
            // 仅持久化稳定失败事实；异常原文不能进入 Agent 表。
            runResultTransaction.recordFailure(initial.run());
            throw new AgentFailurePersistedException();
        }
    }

    private static Map<String, Class<?>> slotTypes(SlotSnapshot slots) {
        Map<String, Class<?>> types = new java.util.LinkedHashMap<>();
        if (slots.values().containsKey("cityCode")) {
            types.put("cityCode", String.class);
        }
        if (slots.values().containsKey("date")) {
            types.put("date", LocalDate.class);
        }
        if (slots.values().containsKey("ticketCount")) {
            types.put("ticketCount", Integer.class);
        }
        if (slots.values().containsKey("genres")) {
            types.put("genres", java.util.List.class);
        }
        if (slots.values().containsKey("movieId")) {
            types.put("movieId", String.class);
        }
        if (slots.values().containsKey("timeFrom")) {
            types.put("timeFrom", LocalTime.class);
        }
        if (slots.values().containsKey("timeTo")) {
            types.put("timeTo", LocalTime.class);
        }
        if (slots.values().containsKey("context.entry")) {
            types.put("context.entry", String.class);
        }
        return Map.copyOf(types);
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

    /** 初始运行已持久化但尚未执行模型的受控上下文；不包含可由客户端伪造的身份数据。 */
    public record ConversationSubmission(AgentMessageSubmissionCommand request, long userId,
            AgentInitialRunResult initial) {

        public ConversationSubmission {
            request = Objects.requireNonNull(request, "提交命令不能为空");
            initial = Objects.requireNonNull(initial, "初始运行不能为空");
        }
    }
}
