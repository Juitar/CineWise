package com.miaoyu.ticket.agent.application.persistence;

import com.miaoyu.ticket.agent.application.run.MinimalReadOnlyAgentResult;
import com.miaoyu.ticket.agent.application.reply.AgentReplyMessageType;
import com.miaoyu.ticket.agent.domain.persistence.AgentMessage;
import com.miaoyu.ticket.agent.domain.persistence.AgentEventType;
import com.miaoyu.ticket.agent.domain.persistence.AgentMessageRole;
import com.miaoyu.ticket.agent.domain.persistence.AgentMessageStatus;
import com.miaoyu.ticket.agent.domain.persistence.AgentMessageType;
import com.miaoyu.ticket.agent.domain.persistence.AgentRun;
import com.miaoyu.ticket.agent.domain.persistence.AgentSession;
import com.miaoyu.ticket.agent.domain.persistence.AgentRunStatus;
import com.miaoyu.ticket.agent.domain.persistence.AgentRunStep;
import com.miaoyu.ticket.agent.domain.persistence.AgentStoredJson;
import com.miaoyu.ticket.agent.domain.plan.ExecutionPlanNode;
import com.miaoyu.ticket.agent.domain.plan.PlanNodeStatus;
import com.miaoyu.ticket.agent.domain.run.ExecutionNodeState;
import com.miaoyu.ticket.agent.domain.run.ExecutionRunState;
import com.miaoyu.ticket.agent.domain.tool.ToolStatus;
import com.miaoyu.ticket.common.config.ClockConfiguration;
import com.miaoyu.ticket.common.id.BusinessIdGenerator;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 只保存已校验计划状态和受控回复的结果短事务。 */
@Service
public class AgentRunResultTransaction {
    private static final String SAFE_FAILURE_TEXT = "智能助手暂时无法完成本次请求，请稍后重试。";
    private static final AgentStoredJson SAFE_FAILURE_PAYLOAD = new AgentStoredJson("{\"reason\":\"RUN_FAILED\"}");

    private final AgentRunRepository runRepository;
    private final AgentRunStepRepository stepRepository;
    private final AgentMessageRepository messageRepository;
    private final AgentSessionRepository sessionRepository;
    private final AgentPersistenceJsonFactory jsonFactory;
    private final AgentRuntimeEventService runtimeEventService;
    private final BusinessIdGenerator idGenerator;
    private final Clock clock;

    public AgentRunResultTransaction(
            AgentRunRepository runRepository,
            AgentRunStepRepository stepRepository,
            AgentMessageRepository messageRepository,
            AgentSessionRepository sessionRepository,
            AgentPersistenceJsonFactory jsonFactory,
            AgentRuntimeEventService runtimeEventService,
            BusinessIdGenerator idGenerator,
            Clock clock) {
        this.runRepository = runRepository;
        this.stepRepository = stepRepository;
        this.messageRepository = messageRepository;
        this.sessionRepository = sessionRepository;
        this.jsonFactory = jsonFactory;
        this.runtimeEventService = runtimeEventService;
        this.idGenerator = idGenerator;
        this.clock = clock;
    }

    /** 写入本轮结果；PROCESSING 保持 RUNNING 和活动会话引用。 */
    @Transactional
    public AgentRun record(AgentRun run, MinimalReadOnlyAgentResult result) {
        Objects.requireNonNull(run, "运行不能为空");
        Objects.requireNonNull(result, "运行结果不能为空");
        LocalDateTime now = now();
        ExecutionRunState state = result.state();
        AgentRunStatus nextStatus = nextRunStatus(state);
        AgentRun nextRun = nextRun(run, state, nextStatus, now);
        boolean updated = nextStatus == AgentRunStatus.RUNNING
                ? runRepository.updateRunningPlanWithCas(nextRun, run.version())
                : runRepository.updateTerminalWithCas(nextRun, run.version());
        if (!updated) {
            throw new IllegalStateException("Agent 运行已由其他事务处理");
        }
        if (state != null) {
            stepRepository.insertAll(state.plan().nodes().stream()
                    .map(node -> toStep(run, state, node, now))
                    .toList());
        }
        messageRepository.insert(assistantMessage(run, result, now));
        recordVisibleEvents(sessionFor(run), nextRun, result, now);
        if (nextStatus.isTerminal()) {
            sessionRepository.releaseActiveRun(run.sessionId(), run.id());
        }
        return nextRun;
    }

    /** 主控或只读工具异常后，用新的短事务写入安全错误并释放仍指向本运行的会话。 */
    @Transactional
    public void recordFailure(AgentRun run) {
        Objects.requireNonNull(run, "运行不能为空");
        LocalDateTime now = now();
        AgentRun failed = nextRun(run, null, AgentRunStatus.FAILED, now);
        if (!runRepository.updateTerminalWithCas(failed, run.version())) {
            return;
        }
        messageRepository.insert(new AgentMessage(
                idGenerator.nextId(), UUID.randomUUID().toString(), run.sessionId(), run.id(), run.userId(),
                AgentMessageRole.ASSISTANT, AgentMessageType.ERROR, SAFE_FAILURE_TEXT, SAFE_FAILURE_PAYLOAD,
                AgentMessageStatus.COMPLETED, now, now, run.expireAt()));
        runtimeEventService.append(sessionFor(run),
                failed, AgentEventType.MESSAGE_ERROR, SAFE_FAILURE_PAYLOAD);
        runtimeEventService.append(sessionFor(run),
                failed, AgentEventType.RUN_COMPLETE, new AgentStoredJson("{\"status\":\"FAILED\"}"));
        sessionRepository.releaseActiveRun(run.sessionId(), run.id());
    }

    private AgentRunStep toStep(AgentRun run, ExecutionRunState state, ExecutionPlanNode node, LocalDateTime now) {
        ExecutionNodeState nodeState = state.nodeState(node.nodeId());
        PlanNodeStatus status = nodeState.status();
        LocalDateTime startedAt = startedAt(status, now);
        LocalDateTime finishedAt = finishedAt(status, now);
        return new AgentRunStep(
                idGenerator.nextId(), run.id(), state.plan().version(), node.nodeId(), node.type(),
                jsonFactory.dependencies(node), jsonFactory.inputReferences(node), status, node.failurePolicy(),
                nodeState.attemptCount(), nodeState.retryCount(), status == PlanNodeStatus.RUNNING,
                nodeState.autoSkipped(), nodeState.skipReason(), nodeState.skipSourceNodeId(),
                jsonFactory.slotSnapshot(node), startedAt, finishedAt, 0L, now, now, run.expireAt());
    }

    private void recordVisibleEvents(
            AgentSession session, AgentRun run, MinimalReadOnlyAgentResult result, LocalDateTime now) {
        ExecutionRunState state = result.state();
        if (state != null) {
            runtimeEventService.append(session, run, AgentEventType.PLAN_CREATED,
                    jsonFactory.eventPayload(Map.of("planVersion", state.plan().version())));
            List<ExecutionPlanNode> toolNodes = new ArrayList<>();
            state.plan().nodes().forEach(node -> {
                PlanNodeStatus status = state.nodeState(node.nodeId()).status();
                if (node.type() == com.miaoyu.ticket.agent.domain.plan.PlanNodeType.CALL_TOOL
                        && status != PlanNodeStatus.PENDING) {
                    toolNodes.add(node);
                    runtimeEventService.append(session, run, AgentEventType.STEP_START,
                            jsonFactory.eventPayload(Map.of("nodeId", node.nodeId())));
                    runtimeEventService.append(session, run, AgentEventType.TOOL_START,
                            jsonFactory.eventPayload(Map.of("nodeId", node.nodeId(), "targetName", node.targetName())));
                } else {
                    recordNodeEvent(session, run, status, node.nodeId());
                }
            });
            recordToolEvents(session, run, result, toolNodes, state);
        } else {
            recordToolEvents(session, run, result, List.of(), null);
        }
        AgentReplyMessageType replyType = result.reply().messageType();
        boolean isCardReply = replyType == AgentReplyMessageType.MOVIE_CARD
                || replyType == AgentReplyMessageType.PLAN_CARD;
        AgentEventType replyEvent = isCardReply
                ? AgentEventType.CARD : replyType == AgentReplyMessageType.ERROR
                        ? AgentEventType.MESSAGE_ERROR : AgentEventType.MESSAGE_COMPLETE;
        AgentStoredJson replyEventPayload = replyEvent == AgentEventType.CARD
                ? jsonFactory.cardPayload(result.reply())
                : jsonFactory.eventPayload(Map.of("messageType", replyType.name()));
        runtimeEventService.append(session, run, replyEvent, replyEventPayload);
        if (run.status().isTerminal()) {
            runtimeEventService.append(session, run, AgentEventType.RUN_COMPLETE,
                    jsonFactory.eventPayload(Map.of("status", run.status().name())));
        }
    }

    private void recordToolEvents(AgentSession session, AgentRun run, MinimalReadOnlyAgentResult result,
            List<ExecutionPlanNode> toolNodes, ExecutionRunState state) {
        for (int index = 0; index < result.toolResults().size(); index++) {
            String nodeId = index < toolNodes.size() ? toolNodes.get(index).nodeId() : null;
            Map<String, String> payload = nodeId == null
                    ? Map.of("status", result.toolResults().get(index).status().name())
                    : Map.of("nodeId", nodeId, "status", result.toolResults().get(index).status().name());
            runtimeEventService.append(session, run, AgentEventType.TOOL_RESULT, jsonFactory.eventPayload(payload));
            if (nodeId != null && state != null) {
                recordTerminalToolNodeEvent(session, run, state.nodeState(nodeId).status(), nodeId);
            }
        }
    }

    private void recordTerminalToolNodeEvent(AgentSession session, AgentRun run, PlanNodeStatus status, String nodeId) {
        if (status == PlanNodeStatus.SUCCESS || status == PlanNodeStatus.SKIPPED || status == PlanNodeStatus.FAILED) {
            recordNodeEvent(session, run, status, nodeId);
        }
    }

    private void recordNodeEvent(AgentSession session, AgentRun run, PlanNodeStatus status, String nodeId) {
        AgentEventType type = switch (status) {
            case SUCCESS, SKIPPED -> AgentEventType.STEP_COMPLETE;
            case FAILED -> AgentEventType.STEP_FAILED;
            case RUNNING -> AgentEventType.STEP_START;
            case PENDING, WAITING_CONFIRMATION -> null;
            default -> null;
        };
        if (type != null) {
            runtimeEventService.append(session, run, type,
                    jsonFactory.eventPayload(Map.of("nodeId", nodeId)));
        }
    }

    private AgentMessage assistantMessage(AgentRun run, MinimalReadOnlyAgentResult result, LocalDateTime now) {
        return new AgentMessage(
                idGenerator.nextId(), UUID.randomUUID().toString(), run.sessionId(), run.id(), run.userId(),
                AgentMessageRole.ASSISTANT, AgentMessageType.valueOf(result.reply().messageType().name()),
                result.reply().text(), jsonFactory.replyPayload(result.reply()), AgentMessageStatus.COMPLETED,
                now, now, run.expireAt());
    }

    private static LocalDateTime startedAt(PlanNodeStatus status, LocalDateTime now) {
        return status == PlanNodeStatus.PENDING
                        || status == PlanNodeStatus.WAITING_CONFIRMATION
                        || status == PlanNodeStatus.SKIPPED
                ? null : now;
    }

    private static LocalDateTime finishedAt(PlanNodeStatus status, LocalDateTime now) {
        return status == PlanNodeStatus.SUCCESS || status == PlanNodeStatus.FAILED || status == PlanNodeStatus.SKIPPED
                ? now
                : null;
    }

    private static AgentRunStatus nextRunStatus(ExecutionRunState state) {
        if (state == null || state.nodeStates().values().stream()
                .anyMatch(node -> node.status() == PlanNodeStatus.FAILED)) {
            return AgentRunStatus.FAILED;
        }
        if (state.nodeStates().values().stream().anyMatch(node -> node.status() == PlanNodeStatus.RUNNING)) {
            return AgentRunStatus.RUNNING;
        }
        if (state.nodeStates().values().stream()
                .anyMatch(node -> node.status() == PlanNodeStatus.WAITING_CONFIRMATION)) {
            return AgentRunStatus.RUNNING;
        }
        if (state.nodeStates().values().stream().anyMatch(node -> node.status() == PlanNodeStatus.PENDING)) {
            return AgentRunStatus.FAILED;
        }
        return AgentRunStatus.COMPLETED;
    }

    private static AgentRun nextRun(
            AgentRun run, ExecutionRunState state, AgentRunStatus status, LocalDateTime now) {
        String planId = state == null ? run.planId() : state.plan().planId();
        Integer planVersion = state == null ? run.planVersion() : Integer.valueOf(state.plan().version());
        return new AgentRun(
                run.id(), run.runId(), run.sessionId(), run.userId(), run.clientRequestId(), run.requestHash(),
                planId, planVersion, status, run.traceId(), run.startedAt(),
                status.isTerminal() ? now : null, run.version() + 1, run.createTime(), now, run.expireAt());
    }

    private LocalDateTime now() {
        return LocalDateTime.ofInstant(clock.instant(), ClockConfiguration.BUSINESS_ZONE_ID);
    }

    private AgentSession sessionFor(AgentRun run) {
        return sessionRepository.findByIdAndUserId(run.sessionId(), run.userId())
                .orElseThrow(() -> new IllegalStateException("运行所属会话不存在"));
    }
}
