package com.miaoyu.ticket.agent.application.persistence;

import com.miaoyu.ticket.agent.application.run.MinimalReadOnlyAgentResult;
import com.miaoyu.ticket.agent.application.AgentDistanceContextApplicationService;
import com.miaoyu.ticket.agent.application.run.AgentRunReplyFactory;
import com.miaoyu.ticket.agent.application.run.MultiToolSupervisorResult;
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
    private final AgentDistanceContextApplicationService distanceContextService;

    public AgentRunResultTransaction(
            AgentRunRepository runRepository,
            AgentRunStepRepository stepRepository,
            AgentMessageRepository messageRepository,
            AgentSessionRepository sessionRepository,
            AgentPersistenceJsonFactory jsonFactory,
            AgentRuntimeEventService runtimeEventService,
            BusinessIdGenerator idGenerator,
            Clock clock,
            AgentDistanceContextApplicationService distanceContextService) {
        this.runRepository = runRepository;
        this.stepRepository = stepRepository;
        this.messageRepository = messageRepository;
        this.sessionRepository = sessionRepository;
        this.jsonFactory = jsonFactory;
        this.runtimeEventService = runtimeEventService;
        this.idGenerator = idGenerator;
        this.clock = clock;
        this.distanceContextService = distanceContextService;
    }

    /** 写入本轮结果；PROCESSING 保持 RUNNING 和活动会话引用。 */
    @Transactional
    public AgentRun record(AgentRun run, MinimalReadOnlyAgentResult result) {
        return recordInternal(run, result, null);
    }

    private AgentRun recordInternal(
            AgentRun run,
            MinimalReadOnlyAgentResult result,
            List<MultiToolSupervisorResult.NodeToolResult> nodeToolResults) {
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
        recordVisibleEvents(sessionFor(run), nextRun, result, nodeToolResults, now);
        if (nextStatus.isTerminal()) {
            sessionRepository.releaseActiveRun(run.sessionId(), run.id());
        }
        return nextRun;
    }

    /**
     * 保存生产提交入口的多工具快照。当前唯一可执行工具是 rankMoviePlan，结果类型仍受白名单约束；
     * 对外只写安全进度或安全错误，不保存模型原文和完整工具响应。
     */
    @Transactional
    public AgentRun record(AgentRun run, MultiToolSupervisorResult result) {
        Objects.requireNonNull(result, "运行结果不能为空");
        return recordInternal(run, AgentRunReplyFactory.asMinimalResult(result, clock.instant()), result.toolResults());
    }


    /** 主控或只读工具异常后，用新的短事务写入安全错误并释放仍指向本运行的会话。 */
    @Transactional
    public void recordFailure(AgentRun run) {
        Objects.requireNonNull(run, "运行不能为空");
        // 仅清理当前进程见过的一次性 ID；不猜测重启前的 ID。
        distanceContextService.cleanupIfPresent(run.runId());
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
            AgentSession session,
            AgentRun run,
            MinimalReadOnlyAgentResult result,
            List<MultiToolSupervisorResult.NodeToolResult> nodeToolResults,
            LocalDateTime now) {
        ExecutionRunState state = result.state();
        if (state != null) {
            runtimeEventService.append(session, run, AgentEventType.PLAN_CREATED,
                    jsonFactory.eventPayload(Map.of("planVersion", state.plan().version())));
            state.plan().nodes().forEach(node -> {
                PlanNodeStatus status = state.nodeState(node.nodeId()).status();
                if (node.type() == com.miaoyu.ticket.agent.domain.plan.PlanNodeType.CALL_TOOL
                        && status != PlanNodeStatus.PENDING) {
                    runtimeEventService.append(session, run, AgentEventType.STEP_START,
                            jsonFactory.eventPayload(Map.of("nodeId", node.nodeId())));
                    String toolName = node.targetName();
                    runtimeEventService.append(session, run, AgentEventType.TOOL_START,
                            jsonFactory.eventPayload(Map.of("nodeId", node.nodeId(), "toolName", toolName,
                                    "displayText", displayText(toolName, false))));
                } else {
                    recordNodeEvent(session, run, status, node.nodeId());
                }
            });
            recordToolEvents(session, run, result, nodeToolResults, state);
        } else {
            recordToolEvents(session, run, result, nodeToolResults, null);
        }
        AgentReplyMessageType replyType = result.reply().messageType();
        boolean isCardReply = replyType == AgentReplyMessageType.MOVIE_CARD
                || replyType == AgentReplyMessageType.PLAN_CARD
                || replyType == AgentReplyMessageType.SELECT_SEATS;
        AgentEventType replyEvent = isCardReply
                ? AgentEventType.CARD : replyType == AgentReplyMessageType.ERROR
                        ? AgentEventType.MESSAGE_ERROR : replyType == AgentReplyMessageType.PROGRESS
                                ? AgentEventType.MESSAGE_START : AgentEventType.MESSAGE_COMPLETE;
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
            List<MultiToolSupervisorResult.NodeToolResult> nodeToolResults,
            ExecutionRunState state) {
        if (nodeToolResults == null) {
            List<String> nodeIds = state == null ? List.of() : state.plan().nodes().stream()
                    .filter(node -> node.type() == com.miaoyu.ticket.agent.domain.plan.PlanNodeType.CALL_TOOL)
                    .map(ExecutionPlanNode::nodeId)
                    .toList();
            nodeToolResults = new ArrayList<>();
            for (int index = 0; index < result.toolResults().size(); index++) {
                String nodeId = index < nodeIds.size() ? nodeIds.get(index) : "tool-" + index;
                nodeToolResults.add(new MultiToolSupervisorResult.NodeToolResult(nodeId,
                        result.toolResults().get(index)));
            }
        }
        for (var toolResult : nodeToolResults) {
            String nodeId = toolResult.nodeId();
            var publicResult = toolResult.result();
            String toolName = toolName(toolResult, state);
            boolean startAlreadyRecorded = state != null && state.nodeStates().containsKey(nodeId)
                    && state.nodeState(nodeId).status() != PlanNodeStatus.PENDING;
            if (!startAlreadyRecorded) {
                Map<String, Object> startPayload = new java.util.LinkedHashMap<>();
                startPayload.put("nodeId", nodeId);
                startPayload.put("toolName", toolName);
                startPayload.put("displayText", displayText(toolName, false));
                runtimeEventService.append(session, run, AgentEventType.TOOL_START,
                        jsonFactory.eventPayload(startPayload));
            }
            if (publicResult.status() == ToolStatus.PROCESSING) {
                continue;
            }
            Map<String, Object> payload = new java.util.LinkedHashMap<>();
            payload.put("nodeId", nodeId);
            payload.put("toolName", toolName);
            payload.put("displayText", displayText(toolName, publicResult.status() == ToolStatus.FAILED));
            AgentEventType eventType = publicResult.status() == ToolStatus.FAILED
                    ? AgentEventType.TOOL_ERROR : AgentEventType.TOOL_COMPLETE;
            if (eventType == AgentEventType.TOOL_ERROR) {
                payload.put("errorCode", publicResult.errorCode() == null ? "TOOL_FAILED" : publicResult.errorCode());
                payload.put("retryable", publicResult.retryable());
                payload.put("replanSuggested", publicResult.replanSuggested());
            } else {
                payload.put("degraded", publicResult.degraded());
                if (publicResult.fallbackType() != null) {
                    payload.put("fallbackType", publicResult.fallbackType());
                }
                if (publicResult.dataAt() != null) {
                    payload.put("dataAt", publicResult.dataAt().toString());
                }
            }
            runtimeEventService.append(session, run, eventType, jsonFactory.eventPayload(payload));
            if (state != null && state.nodeStates().containsKey(nodeId)) {
                recordTerminalToolNodeEvent(session, run, state.nodeState(nodeId).status(), nodeId);
            }
        }
    }

    private static String toolName(MultiToolSupervisorResult.NodeToolResult result, ExecutionRunState state) {
        if (result.targetName() != null && !result.targetName().isBlank()) {
            return result.targetName();
        }
        if (state != null) {
            return state.plan().nodes().stream()
                    .filter(node -> node.nodeId().equals(result.nodeId()))
                    .map(ExecutionPlanNode::targetName)
                    .filter(java.util.Objects::nonNull)
                    .findFirst()
                    .orElse("unknown");
        }
        return "unknown";
    }

    private static String displayText(String toolName, boolean failed) {
        if (failed) {
            return "工具暂时无法完成查询";
        }
        return switch (toolName) {
            case "rankMoviePlan" -> "正在整理推荐方案";
            case "queryAvailableDates" -> "正在查询可用日期";
            case "queryShows" -> "正在查询场次";
            case "querySeats" -> "正在查询座位";
            default -> "正在处理请求";
        };
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
