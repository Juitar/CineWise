package com.miaoyu.ticket.agent.application;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.miaoyu.ticket.agent.api.AgentCardEventValidator;
import com.miaoyu.ticket.agent.application.confirmation.AgentConfirmationService;
import com.miaoyu.ticket.agent.application.persistence.AgentEventReplayService;
import com.miaoyu.ticket.agent.application.persistence.AgentMessageSubmissionService;
import com.miaoyu.ticket.agent.application.persistence.AgentRuntimeQueryService;
import com.miaoyu.ticket.agent.application.persistence.AgentRunCancellationService;
import com.miaoyu.ticket.agent.application.persistence.AgentSessionCreationService;
import com.miaoyu.ticket.agent.application.persistence.AgentSessionManagementService;
import com.miaoyu.ticket.agent.domain.persistence.AgentMessage;
import com.miaoyu.ticket.agent.domain.persistence.AgentRun;
import com.miaoyu.ticket.agent.domain.persistence.AgentSession;
import com.miaoyu.ticket.common.api.PageResult;
import com.miaoyu.ticket.common.config.ClockConfiguration;
import com.miaoyu.ticket.common.observability.TraceIdHolder;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;
import java.util.function.Consumer;
import org.springframework.stereotype.Service;

/** Agent 交互应用门面；HTTP 层不直接依赖持久化用例或持久化领域模型。 */
@Service
public class AgentInteractionRuntimeService {
    private final AgentMessageSubmissionService submissionService;
    private final AgentEventReplayService replayService;
    private final AgentRuntimeQueryService queryService;
    private final AgentSessionCreationService sessionCreationService;
    private final AgentSessionManagementService sessionManagementService;
    private final AgentRunCancellationService runCancellationService;
    private final AgentConfirmationService confirmationService;
    private final ObjectMapper objectMapper;

    public AgentInteractionRuntimeService(AgentMessageSubmissionService submissionService,
            AgentEventReplayService replayService, AgentRuntimeQueryService queryService,
            AgentSessionCreationService sessionCreationService,
            AgentSessionManagementService sessionManagementService,
            AgentRunCancellationService runCancellationService,
            AgentConfirmationService confirmationService,
            ObjectMapper objectMapper) {
        this.submissionService = submissionService;
        this.replayService = replayService;
        this.queryService = queryService;
        this.sessionCreationService = sessionCreationService;
        this.sessionManagementService = sessionManagementService;
        this.runCancellationService = runCancellationService;
        this.confirmationService = confirmationService;
        this.objectMapper = objectMapper;
    }

    /** 只创建空会话；摘要由后续安全运行事实生成，不接收前端伪造的身份或摘要。 */
    public SessionView createMySession() {
        return session(sessionCreationService.createSession());
    }

    public PageResult<SessionView> listMySessions(int page, int size) {
        var result = sessionManagementService.listMySessions(page, size);
        return new PageResult<>(result.total(), result.page(), result.size(), result.records().stream()
                .map(this::session)
                .toList());
    }

    public PageResult<MessageView> listMySessionMessages(String sessionId, int page, int size) {
        var result = sessionManagementService.listMySessionMessages(sessionId, page, size);
        return new PageResult<>(result.total(), result.page(), result.size(), result.records().stream()
                .map(record -> message(record.message(), record.runId()))
                .toList());
    }

    public ClearSessionView clearMySession(String sessionId) {
        var result = sessionManagementService.clearMySession(sessionId);
        return new ClearSessionView(result.sessionId(), result.cleared());
    }

    public BulkClearSessionView clearMySessions() {
        var result = sessionManagementService.clearMySessions();
        return new BulkClearSessionView(result.clearedCount(), result.skippedCount());
    }

    public CancelRunView cancelMyRun(String runId) {
        AgentRun run = runCancellationService.cancelMyRun(runId);
        return new CancelRunView(run.runId(), run.status().name(), time(run.finishedAt()));
    }

    public StreamView submitAndReplay(
            String sessionId, String clientRequestId, String content, String entry, long cursor) {
        var submitted = submissionService.submitConversation(sessionId, content, clientRequestId, entry, 30_000L);
        var replay = replayService.replay(sessionId, cursor);
        return new StreamView(sessionId, submitted.snapshot().run().runId(), replay.reset(), replay.watermark(),
                replay.events().stream().map(this::event).toList());
    }

    /**
     * 先提交初始短事务并重放 {@code message.start}。调用方必须先发送 returned stream，
     * 再调用 {@link #completeConversationAndReplay(StartedSubmission, long)}，从而不让模型耗时阻塞首包。
     */
    public StartedSubmission beginConversationAndReplay(
            String sessionId, String clientRequestId, String content, String entry, long cursor) {
        var submission = submissionService.beginConversation(sessionId, content, clientRequestId, entry, 30_000L);
        var replay = replayService.replay(sessionId, cursor);
        StreamView stream = new StreamView(sessionId, submission.initial().run().runId(), replay.reset(),
                replay.watermark(), replay.events().stream().map(this::event).toList());
        return new StartedSubmission(submission, stream);
    }

    /** 初始事件已发送后继续执行同一运行，并只重放首包水位线之后的新事件。 */
    public StreamView completeConversationAndReplay(StartedSubmission submission, long cursor) {
        return completeConversationAndReplay(submission, cursor, ignored -> {
        });
    }

    /**
     * 将已持久化的普通文本分片立即转换为既有 SSE 事件视图。调用方只能发送该视图，不能自行拼装
     * 模型输出；回调异常不会影响运行完成和后续按游标重放。
     */
    public StreamView completeConversationAndReplay(
            StartedSubmission submission, long cursor, Consumer<EventView> onTextDeltaRecorded) {
        Consumer<EventView> eventConsumer = java.util.Objects.requireNonNull(
                onTextDeltaRecorded, "文本事件回调不能为空");
        AgentRun initialRun = submission.submission().initial().run();
        var submitted = submissionService.completeConversation(submission.submission(), event -> {
            try {
                eventConsumer.accept(event(event, initialRun));
            } catch (RuntimeException ignored) {
                // 当前 SSE 断开后事件仍可由 replay 恢复，不能让回调异常写成运行失败。
            }
        });
        var replay = replayService.replay(submission.stream().sessionId(), cursor);
        return new StreamView(submission.stream().sessionId(), submitted.snapshot().run().runId(), replay.reset(),
                replay.watermark(), replay.events().stream().map(this::event).toList());
    }

    /** 仅重放已经提交的事件，供当前 POST SSE 的安全失败分支使用。 */
    public StreamView replayPersistedEvents(String sessionId, long cursor) {
        var replay = replayService.replay(sessionId, cursor);
        String runId = replay.events().isEmpty() ? null : replay.events().get(replay.events().size() - 1).runId();
        return new StreamView(sessionId, runId, replay.reset(), replay.watermark(),
                replay.events().stream().map(this::event).toList());
    }

    public RunView queryMyRun(String runId) {
        var view = queryService.queryMyRun(runId);
        return new RunView(view.run().runId(), view.session().sessionId(), view.run().status().name(),
                view.run().planId(),
                view.run().planVersion(), time(view.run().startedAt()), time(view.run().finishedAt()),
                Long.toString(view.lastEventId()), view.messages().stream()
                        .map(message -> new RunMessageView(message.messageId(), message.role().name(),
                                message.type().name(), message.text(), time(message.completedAt())))
                        .toList(), view.steps().stream()
                        .map(step -> new StepView(step.nodeId(), step.nodeType().name(), step.status().name(),
                                step.attemptCount(), step.autoSkipped(), step.recoveryPending() ? "RUNNING" : null))
                        .toList(),
                view.events().stream().map(event -> event(event, view.run())).toList());
    }

    private EventView event(com.miaoyu.ticket.agent.domain.persistence.AgentRuntimeEvent event) {
        return event(event, queryService.queryMyRun(event.runId()).run());
    }

    private EventView event(com.miaoyu.ticket.agent.domain.persistence.AgentRuntimeEvent event, AgentRun run) {
        JsonNode payload = refreshConfirmationCard(event.type(), payload(event.payload().value()));
        payload = safeCardPayload(event, run, payload);
        return new EventView(Long.toString(event.eventId()), event.sessionId(), event.runId(), run.planId(),
                run.planVersion(),
                nodeId(payload), event.type().wireValue(), displayText(event.type().wireValue()), payload,
                time(event.createTime()));
    }

    /** 卡片在离开 B 前必须通过固定 Schema；历史脏数据不得直接进入 C 的组件注册表。 */
    private JsonNode safeCardPayload(
            com.miaoyu.ticket.agent.domain.persistence.AgentRuntimeEvent event, AgentRun run, JsonNode payload) {
        if (event.type() != com.miaoyu.ticket.agent.domain.persistence.AgentEventType.CARD) {
            return payload;
        }
        ObjectNode envelope = objectMapper.createObjectNode();
        envelope.put("eventId", Long.toString(event.eventId()));
        envelope.put("eventType", event.type().wireValue());
        envelope.put("sessionId", event.sessionId());
        envelope.put("runId", event.runId());
        envelope.put("occurredAt", time(event.createTime()).toString());
        putNullableText(envelope, "planId", run.planId());
        if (run.planVersion() == null) {
            envelope.putNull("planVersion");
        } else {
            envelope.put("planVersion", run.planVersion());
        }
        putNullableText(envelope, "nodeId", nodeId(payload));
        envelope.put("displayText", displayText(event.type().wireValue()));
        envelope.set("payload", payload);
        AgentCardEventValidator.ValidationResult result = AgentCardEventValidator.validate(envelope);
        if (result.decision() == AgentCardEventValidator.Decision.RENDER) {
            return payload;
        }
        return fallbackCardPayload(result.decision());
    }

    private static void putNullableText(ObjectNode target, String field, String value) {
        if (value == null) {
            target.putNull(field);
        } else {
            target.put(field, value);
        }
    }

    private ObjectNode fallbackCardPayload(AgentCardEventValidator.Decision decision) {
        ObjectNode fallback = objectMapper.createObjectNode();
        if (decision == AgentCardEventValidator.Decision.SAFE_TEXT) {
            fallback.put("type", "TEXT");
            fallback.put("text", "暂不支持该卡片内容");
            fallback.put("format", "PLAIN_TEXT");
            return fallback;
        }
        fallback.put("type", "ERROR");
        fallback.put("code", "206001");
        fallback.put("message", "卡片内容无效");
        fallback.put("retryable", false);
        return fallback;
    }

    private JsonNode refreshConfirmationCard(
            com.miaoyu.ticket.agent.domain.persistence.AgentEventType eventType, JsonNode payload) {
        if (eventType != com.miaoyu.ticket.agent.domain.persistence.AgentEventType.CARD
                || !payload.path("actionId").isTextual()) {
            return payload;
        }
        String actionId = payload.path("actionId").asText();
        return currentAction(actionId)
                .<JsonNode>map(action -> {
                    ObjectNode refreshed = (ObjectNode) payload.deepCopy();
                    refreshed.put("status", com.miaoyu.ticket.agent.domain.confirmation.AgentConfirmationCardStatus
                            .fromActionStatus(action.status()).name());
                    refreshed.put("expireAt", time(action.expireAt()).toString());
                    refreshed.put("type", "PLAN_CARD");
                    if (!refreshed.path("title").isTextual()) {
                        refreshed.put("title", refreshed.path("displayTitle").asText("确认建单"));
                    }
                    if (!refreshed.path("plans").isArray()) {
                        refreshed.putArray("plans");
                    }
                    refreshed.put("source", "agent_confirmation");
                    refreshed.put("dataAt", time(action.updateTime()).toString());
                    refreshed.put("expiresAt", time(action.expireAt()).toString());
                    refreshed.put("degraded", false);
                    return refreshed;
                })
                .orElse(payload);
    }

    /** 重连只按原请求键查询结果未知 action，绝不确认或重发建单。 */
    private java.util.Optional<com.miaoyu.ticket.agent.domain.confirmation.AgentConfirmationAction> currentAction(
            String actionId) {
        var action = confirmationService.refreshForCurrentUser(actionId);
        if (action.isEmpty() || action.get().status()
                != com.miaoyu.ticket.agent.domain.confirmation.AgentConfirmationActionStatus.RESULT_UNKNOWN) {
            return action;
        }
        return java.util.Optional.of(confirmationService.recover(actionId, traceId()).action());
    }

    private static String traceId() {
        String traceId = TraceIdHolder.currentTraceId();
        return traceId.isBlank() ? UUID.randomUUID().toString().replace("-", "") : traceId;
    }

    private static String nodeId(JsonNode payload) {
        return payload.path("nodeId").isTextual() ? payload.path("nodeId").asText() : null;
    }

    private static String displayText(String eventType) {
        return switch (eventType) {
            case "message.start" -> "已接收消息";
            case "message.delta" -> "正在生成回复";
            case "plan.created" -> "已生成执行计划";
            case "step.start" -> "步骤执行中";
            case "step.complete" -> "步骤已完成";
            case "step.failed" -> "步骤执行失败";
            case "tool.start" -> "工具执行中";
            case "tool.complete" -> "工具执行完成";
            case "tool.error" -> "工具执行失败";
            case "tool.result" -> "已获得推荐结果";
            case "card" -> "已生成推荐卡片";
            case "message.complete" -> "已生成回复";
            case "message.error" -> "本次请求未完成";
            case "run.complete" -> "运行已结束";
            default -> "运行状态已更新";
        };
    }

    private JsonNode payload(String json) {
        try {
            return objectMapper.readTree(json);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("已持久化 Agent 事件载荷不是 JSON 对象", exception);
        }
    }

    private SessionView session(AgentSession session) {
        return new SessionView(session.sessionId(), session.summary(), session.status().name(),
                time(session.createTime()), time(session.updateTime()));
    }

    private MessageView message(AgentMessage message, String runId) {
        return new MessageView(message.messageId(), message.role().name(), message.type().name(), message.text(),
                runId, message.payload() == null ? null : payload(message.payload().value()), message.status().name(),
                time(message.completedAt()), time(message.createTime()));
    }

    private static OffsetDateTime time(LocalDateTime time) {
        return time == null ? null : time.atZone(ClockConfiguration.BUSINESS_ZONE_ID).toOffsetDateTime();
    }

    public record StreamView(String sessionId, String runId, boolean reset, long watermark, List<EventView> events) {
    }
    /** 初始短事务与它已可见的 SSE 事件；只在当前服务内传递，不能由 HTTP 请求构造。 */
    public record StartedSubmission(
            com.miaoyu.ticket.agent.application.persistence.AgentMessageSubmissionService
                    .ConversationSubmission submission,
            StreamView stream) {
    }
    public record RunView(String runId, String sessionId, String status, String planId, Integer planVersion,
            OffsetDateTime startedAt, OffsetDateTime finishedAt, String lastEventId, List<RunMessageView> messages,
            List<StepView> steps, List<EventView> events) {
    }
    public record RunMessageView(String messageId, String role, String type, String text, OffsetDateTime completedAt) {
    }
    public record SessionView(String sessionId, String summary, String status, OffsetDateTime createdAt,
            OffsetDateTime updatedAt) {
    }
    public record ClearSessionView(String sessionId, boolean cleared) {
    }
    public record BulkClearSessionView(int clearedCount, int skippedCount) {
    }
    public record CancelRunView(String runId, String status, OffsetDateTime finishedAt) {
    }
    public record MessageView(String messageId, String role, String type, String text, String runId, JsonNode payload,
            String status,
            OffsetDateTime completedAt, OffsetDateTime createdAt) {
    }
    public record StepView(String nodeId, String nodeType, String status, int attemptCount, boolean autoSkipped,
            String recoveryHint) {
    }
    public record EventView(String eventId, String sessionId, String runId, String planId, Integer planVersion,
            String nodeId,
            String eventType, String displayText, JsonNode payload, OffsetDateTime occurredAt) {
    }
}
