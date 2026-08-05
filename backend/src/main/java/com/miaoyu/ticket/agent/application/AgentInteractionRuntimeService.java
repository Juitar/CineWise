package com.miaoyu.ticket.agent.application;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.miaoyu.ticket.agent.application.persistence.AgentEventReplayService;
import com.miaoyu.ticket.agent.application.persistence.AgentMessageSubmissionCommand;
import com.miaoyu.ticket.agent.application.persistence.AgentMessageSubmissionService;
import com.miaoyu.ticket.agent.application.persistence.AgentRuntimeQueryService;
import com.miaoyu.ticket.agent.domain.plan.PlanValidationContext;
import com.miaoyu.ticket.agent.domain.plan.SlotSnapshot;
import com.miaoyu.ticket.common.config.ClockConfiguration;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;

/** Agent 交互应用门面；HTTP 层不直接依赖持久化用例或持久化领域模型。 */
@Service
public class AgentInteractionRuntimeService {
    private final AgentMessageSubmissionService submissionService;
    private final AgentEventReplayService replayService;
    private final AgentRuntimeQueryService queryService;
    private final ObjectMapper objectMapper;

    public AgentInteractionRuntimeService(AgentMessageSubmissionService submissionService,
            AgentEventReplayService replayService, AgentRuntimeQueryService queryService, ObjectMapper objectMapper) {
        this.submissionService = submissionService;
        this.replayService = replayService;
        this.queryService = queryService;
        this.objectMapper = objectMapper;
    }

    public StreamView submitAndReplay(
            String sessionId, String clientRequestId, String content, String entry, long cursor) {
        SlotSnapshot slots = new SlotSnapshot(1L, Map.of("context.entry", entry));
        var submitted = submissionService.submit(new AgentMessageSubmissionCommand(sessionId, content, clientRequestId,
                slots, new PlanValidationContext(Map.of(), Map.of(), slots), 30_000L));
        var replay = replayService.replay(sessionId, cursor);
        return new StreamView(sessionId, submitted.snapshot().run().runId(), replay.reset(), replay.watermark(),
                replay.events().stream().map(event -> event(event, submitted.snapshot().run().planVersion())).toList());
    }

    /** 仅重放已经提交的事件，供当前 POST SSE 的安全失败分支使用。 */
    public StreamView replayPersistedEvents(String sessionId, long cursor) {
        var replay = replayService.replay(sessionId, cursor);
        String runId = replay.events().isEmpty() ? null : replay.events().get(replay.events().size() - 1).runId();
        return new StreamView(sessionId, runId, replay.reset(), replay.watermark(),
                replay.events().stream().map(event -> event(event, null)).toList());
    }

    public RunView queryMyRun(String runId) {
        var view = queryService.queryMyRun(runId);
        return new RunView(view.run().runId(), view.session().sessionId(), view.run().status().name(),
                view.run().planId(),
                view.run().planVersion(), time(view.run().startedAt()), time(view.run().finishedAt()),
                Long.toString(view.lastEventId()), view.messages().stream()
                        .map(message -> new MessageView(message.messageId(), message.role().name(),
                                message.type().name(), message.text(), time(message.completedAt())))
                        .toList(), view.steps().stream()
                        .map(step -> new StepView(step.nodeId(), step.nodeType().name(), step.status().name(),
                                step.attemptCount(), step.autoSkipped(), step.recoveryPending() ? "RUNNING" : null))
                        .toList(),
                view.events().stream().map(event -> event(event, view.run().planVersion())).toList());
    }

    private EventView event(com.miaoyu.ticket.agent.domain.persistence.AgentRuntimeEvent event, Integer planVersion) {
        JsonNode payload = payload(event.payload().value());
        return new EventView(Long.toString(event.eventId()), event.sessionId(), event.runId(), planVersion,
                nodeId(payload), event.type().wireValue(), displayText(event.type().wireValue()), payload,
                time(event.createTime()));
    }

    private static String nodeId(JsonNode payload) {
        return payload.path("nodeId").isTextual() ? payload.path("nodeId").asText() : null;
    }

    private static String displayText(String eventType) {
        return switch (eventType) {
            case "message.start" -> "已接收消息";
            case "plan.created" -> "已生成执行计划";
            case "step.start" -> "步骤执行中";
            case "step.complete" -> "步骤已完成";
            case "step.failed" -> "步骤执行失败";
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

    private static OffsetDateTime time(LocalDateTime time) {
        return time == null ? null : time.atZone(ClockConfiguration.BUSINESS_ZONE_ID).toOffsetDateTime();
    }

    public record StreamView(String sessionId, String runId, boolean reset, long watermark, List<EventView> events) {
    }
    public record RunView(String runId, String sessionId, String status, String planId, Integer planVersion,
            OffsetDateTime startedAt, OffsetDateTime finishedAt, String lastEventId, List<MessageView> messages,
            List<StepView> steps, List<EventView> events) {
    }
    public record MessageView(String messageId, String role, String type, String text, OffsetDateTime completedAt) {
    }
    public record StepView(String nodeId, String nodeType, String status, int attemptCount, boolean autoSkipped,
            String recoveryHint) {
    }
    public record EventView(String eventId, String sessionId, String runId, Integer planVersion, String nodeId,
            String eventType, String displayText, JsonNode payload, OffsetDateTime occurredAt) {
    }
}
