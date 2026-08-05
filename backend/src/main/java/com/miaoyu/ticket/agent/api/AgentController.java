package com.miaoyu.ticket.agent.api;

import com.miaoyu.ticket.agent.application.AgentInteractionRuntimeService;
import com.miaoyu.ticket.agent.application.AgentFailurePersistedException;
import com.miaoyu.ticket.common.api.Result;
import com.miaoyu.ticket.common.api.PageResult;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.time.Duration;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicReference;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.http.MediaType;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.security.concurrent.DelegatingSecurityContextExecutor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import org.springframework.validation.annotation.Validated;

/** Agent HTTP 入口只委托应用服务，用户归属由 CurrentUserAccessor 统一校验。 */
@RestController
@Validated
@RequestMapping("/api/v1/agent")
public class AgentController {
    private static final Duration HEARTBEAT_INTERVAL = Duration.ofSeconds(10);
    private final AgentInteractionRuntimeService runtimeService;
    private final Executor applicationTaskExecutor;
    private final ObjectProvider<TaskScheduler> taskSchedulerProvider;
    private final ObjectMapper objectMapper;

    public AgentController(AgentInteractionRuntimeService runtimeService,
            @Qualifier("applicationTaskExecutor") Executor applicationTaskExecutor,
            @Qualifier("taskScheduler") ObjectProvider<TaskScheduler> taskSchedulerProvider,
            ObjectMapper objectMapper) {
        this.runtimeService = runtimeService;
        this.applicationTaskExecutor = applicationTaskExecutor;
        this.taskSchedulerProvider = taskSchedulerProvider;
        this.objectMapper = objectMapper;
    }

    @PostMapping("/sessions")
    public Result<AgentSessionResponse> createMySession() {
        return Result.success(session(runtimeService.createMySession()));
    }

    @GetMapping("/sessions")
    public Result<PageResult<AgentSessionResponse>> listMySessions(
            @RequestParam(defaultValue = "1") @Min(1) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
        PageResult<AgentInteractionRuntimeService.SessionView> view = runtimeService.listMySessions(page, size);
        PageResult<AgentSessionResponse> response = new PageResult<>(view.total(), view.page(), view.size(),
                view.records().stream().map(this::session).toList());
        return Result.success(response);
    }

    @GetMapping("/sessions/{sessionId}/messages")
    public Result<PageResult<AgentMessageResponse>> listMySessionMessages(
            @PathVariable String sessionId,
            @RequestParam(defaultValue = "1") @Min(1) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
        PageResult<AgentInteractionRuntimeService.MessageView> view =
                runtimeService.listMySessionMessages(sessionId, page, size);
        PageResult<AgentMessageResponse> response = new PageResult<>(view.total(), view.page(), view.size(),
                view.records().stream().map(this::message).toList());
        return Result.success(response);
    }

    @DeleteMapping("/sessions/{sessionId}")
    public Result<AgentSessionClearResponse> clearMySession(@PathVariable String sessionId) {
        var result = runtimeService.clearMySession(sessionId);
        return Result.success(new AgentSessionClearResponse(result.sessionId(), result.cleared()));
    }

    @DeleteMapping("/sessions")
    public Result<AgentSessionBulkClearResponse> clearMySessions() {
        var result = runtimeService.clearMySessions();
        return Result.success(new AgentSessionBulkClearResponse(result.clearedCount(), result.skippedCount()));
    }

    @PostMapping("/runs/{runId}/cancel")
    public Result<AgentRunCancelResponse> cancelMyRun(@PathVariable String runId) {
        var result = runtimeService.cancelMyRun(runId);
        return Result.success(new AgentRunCancelResponse(result.runId(), result.status(), result.finishedAt()));
    }

    @GetMapping("/runs/{runId}")
    public Result<AgentRunResponse> getMyRun(@PathVariable String runId) {
        var view = runtimeService.queryMyRun(runId);
        return Result.success(new AgentRunResponse(view.runId(), view.sessionId(), view.status(), view.planId(),
                view.planVersion(), view.startedAt(), view.finishedAt(), view.lastEventId(),
                view.messages().stream().map(message -> new AgentRunResponse.MessageSummary(message.messageId(),
                        message.role(), message.type(), message.text(), message.completedAt())).toList(),
                view.steps().stream().map(step -> new AgentRunResponse.StepSummary(step.nodeId(), step.nodeType(),
                        step.status(), step.attemptCount(), step.autoSkipped(), step.recoveryHint())).toList(),
                view.events().stream().map(this::eventSummary).toList()));
    }

    @PostMapping(value = "/sessions/{sessionId}/messages/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter submitAndStream(@PathVariable String sessionId,
            @RequestHeader(value = "Last-Event-ID", required = false) String lastEventId,
            @Valid @RequestBody AgentMessageStreamRequest request) {
        long cursor = parseCursor(lastEventId);
        SseEmitter emitter = new SseEmitter(30_000L);
        AtomicReference<ScheduledFuture<?>> heartbeat = new AtomicReference<>();
        Runnable cancelHeartbeat = () -> cancelHeartbeat(heartbeat);
        emitter.onCompletion(cancelHeartbeat);
        emitter.onError(error -> cancelHeartbeat.run());
        emitter.onTimeout(() -> {
            cancelHeartbeat.run();
            emitter.complete();
        });
        TaskScheduler taskScheduler = taskSchedulerProvider.getIfAvailable();
        if (taskScheduler != null) {
            heartbeat.set(taskScheduler.scheduleAtFixedRate(() -> sendHeartbeat(emitter), HEARTBEAT_INTERVAL));
        }
        try {
            new DelegatingSecurityContextExecutor(applicationTaskExecutor).execute(() -> {
                try {
                    var submission = runtimeService.submitAndReplay(
                            sessionId, request.clientRequestId(), request.content(), request.context().entry(), cursor);
                    writeEvents(emitter, submission);
                } catch (AgentFailurePersistedException exception) {
                    writeEvents(emitter, runtimeService.replayPersistedEvents(sessionId, cursor));
                }
            });
        } catch (RuntimeException exception) {
            cancelHeartbeat.run();
            throw exception;
        }
        return emitter;
    }

    private static void cancelHeartbeat(AtomicReference<ScheduledFuture<?>> heartbeat) {
        ScheduledFuture<?> future = heartbeat.getAndSet(null);
        if (future != null) {
            future.cancel(false);
        }
    }

    private void sendHeartbeat(SseEmitter emitter) {
        try {
            emitter.send(SseEmitter.event().comment("heartbeat"));
        } catch (IOException exception) {
            emitter.completeWithError(exception);
        }
    }

    private void writeEvents(
            SseEmitter emitter, AgentInteractionRuntimeService.StreamView replay) {
        try {
            if (replay.reset()) {
                emitter.send(SseEmitter.event().id(Long.toString(replay.watermark())).name("stream.reset")
                        .data(new AgentRunResponse.EventSummary(Long.toString(replay.watermark()), replay.sessionId(),
                                replay.runId(), null, null, "stream.reset", "已清理的事件不可续传",
                                payload("{\"watermark\":\"" + replay.watermark() + "\"}"), null)));
            }
            for (var event : replay.events()) {
                emitter.send(SseEmitter.event().id(event.eventId()).name(event.eventType()).data(eventSummary(event)));
            }
            emitter.complete();
        } catch (IOException exception) {
            emitter.completeWithError(exception);
        }
    }

    private static long parseCursor(String value) {
        if (value == null || value.isBlank()) {
            return 0L;
        }
        try {
            long cursor = Long.parseLong(value);
            if (cursor < 0) {
                throw new NumberFormatException();
            }
            return cursor;
        } catch (NumberFormatException exception) {
            throw new IllegalArgumentException("Last-Event-ID 必须是非负十进制整数");
        }
    }

    private AgentRunResponse.EventSummary eventSummary(AgentInteractionRuntimeService.EventView event) {
        return new AgentRunResponse.EventSummary(event.eventId(), event.sessionId(), event.runId(), event.planVersion(),
                event.nodeId(), event.eventType(), event.displayText(), event.payload(), event.occurredAt());
    }

    private AgentSessionResponse session(AgentInteractionRuntimeService.SessionView view) {
        return new AgentSessionResponse(view.sessionId(), view.summary(), view.status(), view.createdAt(),
                view.updatedAt());
    }

    private AgentMessageResponse message(AgentInteractionRuntimeService.MessageView view) {
        return new AgentMessageResponse(view.messageId(), view.role(), view.type(), view.text(), view.payload(),
                view.status(), view.completedAt(), view.createdAt());
    }

    private JsonNode payload(String value) {
        try {
            return objectMapper.readTree(value);
        } catch (com.fasterxml.jackson.core.JsonProcessingException exception) {
            throw new IllegalStateException("固定 SSE 重置载荷无效", exception);
        }
    }
}
