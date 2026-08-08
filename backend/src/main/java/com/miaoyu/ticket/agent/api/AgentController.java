package com.miaoyu.ticket.agent.api;

import com.miaoyu.ticket.agent.application.AgentInteractionRuntimeService;
import com.miaoyu.ticket.agent.application.AgentFailurePersistedException;
import com.miaoyu.ticket.agent.application.confirmation.AgentConfirmationService;
import com.miaoyu.ticket.common.config.ClockConfiguration;
import com.miaoyu.ticket.common.api.Result;
import com.miaoyu.ticket.common.api.PageResult;
import com.miaoyu.ticket.common.error.BusinessException;
import com.miaoyu.ticket.common.observability.TraceIdHolder;
import com.miaoyu.ticket.agent.application.AgentErrorCode;
import com.miaoyu.ticket.agent.application.confirmation.AgentConfirmationResult;
import com.miaoyu.ticket.agent.domain.confirmation.AgentConfirmationActionStatus;
import com.miaoyu.ticket.agent.domain.confirmation.AgentConfirmationValidationFailure;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import java.util.UUID;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.http.MediaType;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.security.concurrent.DelegatingSecurityContextExecutor;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
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
    private final AgentConfirmationService confirmationService;
    private final Executor applicationTaskExecutor;
    private final ObjectProvider<TaskScheduler> taskSchedulerProvider;
    private final ObjectMapper objectMapper;

    public AgentController(AgentInteractionRuntimeService runtimeService,
            AgentConfirmationService confirmationService,
            @Qualifier("applicationTaskExecutor") Executor applicationTaskExecutor,
            @Qualifier("taskScheduler") ObjectProvider<TaskScheduler> taskSchedulerProvider,
            ObjectMapper objectMapper) {
        this.runtimeService = runtimeService;
        this.confirmationService = confirmationService;
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

    @PostMapping("/actions/{actionId}/confirm")
    public Result<AgentActionResponse> confirmAction(
            @PathVariable String actionId, @Valid @RequestBody AgentActionConfirmRequest request) {
        AgentConfirmationResult result = confirmationService.confirm(actionId, request.confirmed(), traceId());
        throwIfConfirmationFailed(result, request.confirmed());
        return Result.success(actionResponse(result));
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
            @Valid @RequestBody AgentMessageStreamRequest request,
            HttpServletResponse response) {
        long cursor = parseCursor(lastEventId);
        // 初始短事务必须留在请求线程：活动运行冲突等业务异常应直接返回 HTTP 错误，
        // 不能在 SSE 已经返回 200 后丢到后台线程，导致页面永远停在“思考中”。
        AgentInteractionRuntimeService.StartedSubmission started =
                runtimeService.beginConversationAndReplay(sessionId, request.clientRequestId(),
                        request.content(), request.context().entry(), cursor);
        response.setHeader("Cache-Control", "no-cache, no-transform");
        response.setHeader("X-Accel-Buffering", "no");
        SseEmitter emitter = new SseEmitter(0L);
        AtomicReference<ScheduledFuture<?>> heartbeat = new AtomicReference<>();
        Runnable cancelHeartbeat = () -> cancelHeartbeat(heartbeat);
        SseDisconnectHandler disconnectHandler = new SseDisconnectHandler(cancelHeartbeat);
        emitter.onCompletion(cancelHeartbeat);
        emitter.onError(error -> disconnectHandler.onDisconnected());
        emitter.onTimeout(() -> {
            cancelHeartbeat.run();
            emitter.complete();
        });
        TaskScheduler taskScheduler = taskSchedulerProvider.getIfAvailable();
        if (taskScheduler != null) {
            heartbeat.set(taskScheduler.scheduleAtFixedRate(() -> sendHeartbeat(emitter), HEARTBEAT_INTERVAL));
        }
        // 请求结束时 Spring 会清空原 SecurityContext，不能把原对象直接交给延迟任务；
        // 这里复制认证信息，保证 SseEmitter 建立后后台 Agent 仍能获得当前用户。
        SecurityContext securityContext = SecurityContextHolder.createEmptyContext();
        securityContext.setAuthentication(SecurityContextHolder.getContext().getAuthentication());
        Executor authenticatedExecutor =
                new DelegatingSecurityContextExecutor(applicationTaskExecutor, securityContext);
        Runnable beginAfterEmitterReady = () -> {
            // 先提交响应头和一个完整 SSE 注释块，浏览器无需等待数据库或模型即可建立流。
            sendHeartbeat(emitter);
            authenticatedExecutor.execute(() -> {
                try {
                    // 初始短事务已提交；首包必须在模型和工具执行前发出，但不能关闭 SSE。
                    writeEvents(emitter, started.stream(), false);
                    long replayCursor = lastSentEventId(started.stream(), cursor);
                    AtomicLong sentCursor = new AtomicLong(replayCursor);
                    var completion = runtimeService.completeConversationAndReplay(started, replayCursor,
                            event -> {
                                writeEvent(emitter, event);
                                sentCursor.accumulateAndGet(Long.parseLong(event.eventId()), Math::max);
                            });
                    writeEvents(emitter, onlyAfter(completion, sentCursor.get()), true);
                } catch (AgentFailurePersistedException exception) {
                    long replayCursor = lastSentEventId(started.stream(), cursor);
                    writeEvents(emitter, onlyAfter(runtimeService.replayPersistedEvents(sessionId, replayCursor),
                            replayCursor), true);
                } catch (RuntimeException exception) {
                    cancelHeartbeat.run();
                    emitter.completeWithError(exception);
                }
            });
        };
        try {
            if (taskScheduler != null) {
                // 让 MVC 完成 SseEmitter 绑定后再写首事件，避免被容器缓存到整条响应结束。
                taskScheduler.schedule(beginAfterEmitterReady, Instant.now().plusMillis(25));
            } else {
                beginAfterEmitterReady.run();
            }
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
            SseEmitter emitter, AgentInteractionRuntimeService.StreamView replay, boolean complete) {
        try {
            if (replay.reset()) {
                emitter.send(SseEmitter.event().id(Long.toString(replay.watermark())).name("stream.reset")
                        .data(new AgentCardEventResponse(Long.toString(replay.watermark()), replay.sessionId(),
                                replay.runId(), null, null, null, "stream.reset", "已清理的事件不可续传",
                                cardPayload("{\"watermark\":\"" + replay.watermark() + "\"}"), null)));
            }
            for (var event : replay.events()) {
                writeEvent(emitter, event);
            }
            if (complete) {
                emitter.complete();
            }
        } catch (IOException exception) {
            emitter.completeWithError(exception);
        }
    }

    /** 已提交的文本分片必须马上写入当前 SSE；写失败只结束这条连接，事件仍可按游标恢复。 */
    private void writeEvent(SseEmitter emitter, AgentInteractionRuntimeService.EventView event) {
        try {
            emitter.send(SseEmitter.event().id(event.eventId()).name(event.eventType()).data(eventSummary(event)));
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

    private static long lastSentEventId(AgentInteractionRuntimeService.StreamView stream, long fallback) {
        if (stream.events().isEmpty()) {
            return fallback;
        }
        return Long.parseLong(stream.events().getLast().eventId());
    }

    /** 流式文本已经实时写过，最终 replay 只补尚未发送的计划、卡片和完成事件。 */
    private static AgentInteractionRuntimeService.StreamView onlyAfter(
            AgentInteractionRuntimeService.StreamView stream, long cursor) {
        return new AgentInteractionRuntimeService.StreamView(stream.sessionId(), stream.runId(), stream.reset(),
                stream.watermark(), stream.events().stream()
                        .filter(event -> Long.parseLong(event.eventId()) > cursor)
                        .toList());
    }

    private AgentCardEventResponse eventSummary(AgentInteractionRuntimeService.EventView event) {
        return new AgentCardEventResponse(event.eventId(), event.sessionId(), event.runId(), event.planId(),
                event.planVersion(), event.nodeId(), event.eventType(), event.displayText(),
                AgentCardPayloadResponse.from(event.payload()),
                event.occurredAt());
    }

    private AgentSessionResponse session(AgentInteractionRuntimeService.SessionView view) {
        return new AgentSessionResponse(view.sessionId(), view.summary(), view.status(), view.createdAt(),
                view.updatedAt());
    }

    private AgentActionResponse actionResponse(AgentConfirmationResult result) {
        var action = result.action();
        return new AgentActionResponse(action.actionId(), action.runId(), action.planVersion(),
                com.miaoyu.ticket.agent.domain.confirmation.AgentConfirmationCardStatus.fromActionStatus(
                        action.status()),
                action.updateTime().atZone(ClockConfiguration.BUSINESS_ZONE_ID).toOffsetDateTime());
    }

    private static void throwIfConfirmationFailed(AgentConfirmationResult result, boolean confirmed) {
        AgentConfirmationValidationFailure failure = result.validationFailure();
        if (failure == null && !(confirmed && !result.writeToolInvoked()
                && (result.action().status() == AgentConfirmationActionStatus.EXECUTING
                || result.action().status() == AgentConfirmationActionStatus.RESULT_UNKNOWN))) {
            return;
        }
        if (failure == AgentConfirmationValidationFailure.ACTION_NOT_CONFIRMABLE
                && result.action().status().isTerminal()) {
            return;
        }
        if (failure == AgentConfirmationValidationFailure.EXPIRED) {
            throw new BusinessException(AgentErrorCode.ACTION_EXPIRED);
        }
        if (failure == AgentConfirmationValidationFailure.PARAMETERS_CHANGED
                || failure == AgentConfirmationValidationFailure.PLAN_CHANGED
                || failure == AgentConfirmationValidationFailure.NODE_NOT_WAITING_CONFIRMATION
                || failure == AgentConfirmationValidationFailure.BUSINESS_DATA_INVALID) {
            throw new BusinessException(AgentErrorCode.ACTION_PARAMETER_CHANGED);
        }
        if (failure == AgentConfirmationValidationFailure.NOT_OWNER
                || failure == AgentConfirmationValidationFailure.RUN_ENDED) {
            throw new BusinessException(AgentErrorCode.AGENT_RESOURCE_NOT_FOUND, "操作不可用或已失效");
        }
        throw new BusinessException(AgentErrorCode.ACTION_CONFIRMING);
    }

    private static String traceId() {
        String traceId = TraceIdHolder.currentTraceId();
        return traceId.isBlank() ? UUID.randomUUID().toString().replace("-", "") : traceId;
    }

    private AgentMessageResponse message(AgentInteractionRuntimeService.MessageView view) {
        return new AgentMessageResponse(view.messageId(), view.role(), view.type(), view.text(), view.runId(),
                view.payload(), view.status(), view.completedAt(), view.createdAt());
    }

    private AgentCardPayloadResponse cardPayload(String value) {
        try {
            return AgentCardPayloadResponse.from(objectMapper.readTree(value));
        } catch (com.fasterxml.jackson.core.JsonProcessingException exception) {
            throw new IllegalStateException("固定 SSE 重置载荷无效", exception);
        }
    }
}
