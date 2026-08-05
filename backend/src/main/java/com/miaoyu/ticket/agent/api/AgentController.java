package com.miaoyu.ticket.agent.api;

import com.miaoyu.ticket.agent.application.AgentInteractionRuntimeService;
import com.miaoyu.ticket.common.api.Result;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.time.Duration;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicReference;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.http.MediaType;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.security.concurrent.DelegatingSecurityContextExecutor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

/** Agent HTTP 入口只委托应用服务，用户归属由 CurrentUserAccessor 统一校验。 */
@RestController
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
                var submission = runtimeService.submitAndReplay(sessionId, request.clientRequestId(), request.content(),
                        request.context().entry(), cursor);
                writeEvents(emitter, submission);
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

    private JsonNode payload(String value) {
        try {
            return objectMapper.readTree(value);
        } catch (com.fasterxml.jackson.core.JsonProcessingException exception) {
            throw new IllegalStateException("固定 SSE 重置载荷无效", exception);
        }
    }
}
