package com.miaoyu.ticket.agent;

import static org.hamcrest.Matchers.nullValue;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.request;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.miaoyu.ticket.agent.api.AgentController;
import com.miaoyu.ticket.agent.application.AgentFailurePersistedException;
import com.miaoyu.ticket.agent.application.AgentInteractionRuntimeService;
import com.miaoyu.ticket.agent.application.confirmation.AgentConfirmationService;
import com.miaoyu.ticket.agent.application.confirmation.AgentConfirmationResult;
import com.miaoyu.ticket.agent.application.AgentErrorCode;
import com.miaoyu.ticket.common.api.PageResult;
import com.miaoyu.ticket.common.error.BusinessException;
import com.miaoyu.ticket.common.error.GlobalExceptionHandler;
import com.miaoyu.ticket.agent.domain.confirmation.AgentConfirmationAction;
import com.miaoyu.ticket.agent.domain.confirmation.AgentConfirmationValidationFailure;
import com.miaoyu.ticket.agent.domain.confirmation.ConfirmedOrderCommand;
import com.miaoyu.ticket.agent.domain.confirmation.AgentActionWriteIdentifiers;
import java.time.OffsetDateTime;
import java.time.LocalDateTime;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.Executor;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.scheduling.TaskScheduler;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.http.MediaType;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;

/** Controller 只映射应用门面输出，不泄露内部运行 ID 或持久化对象。 */
class AgentControllerTest {
    private AgentInteractionRuntimeService runtimeService;
    private AgentConfirmationService confirmationService;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        runtimeService = Mockito.mock(AgentInteractionRuntimeService.class);
        confirmationService = Mockito.mock(AgentConfirmationService.class);
        Executor directExecutor = Runnable::run;
        TaskScheduler taskScheduler = Mockito.mock(TaskScheduler.class);
        @SuppressWarnings("unchecked")
        ObjectProvider<TaskScheduler> taskSchedulerProvider = Mockito.mock(ObjectProvider.class);
        when(taskSchedulerProvider.getIfAvailable()).thenReturn(taskScheduler);
        when(taskScheduler.schedule(org.mockito.ArgumentMatchers.any(Runnable.class),
                org.mockito.ArgumentMatchers.any(Instant.class))).thenAnswer(invocation -> {
                    invocation.getArgument(0, Runnable.class).run();
                    return null;
                });
        ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules()
                .enable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);
        mockMvc = MockMvcBuilders.standaloneSetup(
                new AgentController(runtimeService, confirmationService, directExecutor, taskSchedulerProvider,
                        objectMapper))
                .setControllerAdvice(new GlobalExceptionHandler())
                .setMessageConverters(new MappingJackson2HttpMessageConverter(objectMapper))
                .build();
    }

    @Test
    void shouldReturnCurrentUserRunSnapshot() throws Exception {
        when(runtimeService.queryMyRun("run-1")).thenReturn(new AgentInteractionRuntimeService.RunView(
                "run-1", "session-1", "COMPLETED", "plan-1", 1,
                OffsetDateTime.parse("2026-08-05T10:00:00+08:00"), OffsetDateTime.parse("2026-08-05T10:00:01+08:00"),
                "9", List.of(), List.of(), List.of()));

        mockMvc.perform(get("/api/v1/agent/runs/run-1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.runId").value("run-1"))
                .andExpect(jsonPath("$.data.sessionId").value("session-1"))
                .andExpect(jsonPath("$.data.lastEventId").value("9"));
    }

    @Test
    void shouldConfirmUsingOnlyTheBooleanRequestBody() throws Exception {
        AgentConfirmationAction rejected = action().reject(LocalDateTime.of(2026, 8, 5, 10, 1));
        when(confirmationService.confirm(eq("action-1"), eq(false), anyString()))
                .thenReturn(new AgentConfirmationResult(rejected, null, false));

        mockMvc.perform(post("/api/v1/agent/actions/action-1/confirm")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"confirmed\":false}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.actionId").value("action-1"))
                .andExpect(jsonPath("$.data.status").value("REJECTED"))
                .andExpect(jsonPath("$.data.showId").doesNotExist());

        verify(confirmationService).confirm(eq("action-1"), eq(false), anyString());
    }

    @Test
    void shouldRejectAnyTransactionFieldOutsideConfirmed() throws Exception {
        mockMvc.perform(post("/api/v1/agent/actions/action-1/confirm")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"confirmed":true,"showId":"70001","seatIds":["2","4"],
                         "totalAmount":"999.99","idempotencyKey":"attacker-key"}
                        """))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(confirmationService);
    }

    @Test
    void shouldReturnConfiguredSafeErrorForExpiredOrExecutingAction() throws Exception {
        when(confirmationService.confirm(eq("expired"), eq(true), anyString()))
                .thenReturn(new AgentConfirmationResult(action(), AgentConfirmationValidationFailure.EXPIRED, false));
        when(confirmationService.confirm(eq("executing"), eq(true), anyString()))
                .thenReturn(new AgentConfirmationResult(executingAction(), null, false));

        mockMvc.perform(post("/api/v1/agent/actions/expired/confirm")
                .contentType(MediaType.APPLICATION_JSON).content("{\"confirmed\":true}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value(206003));
        mockMvc.perform(post("/api/v1/agent/actions/executing/confirm")
                .contentType(MediaType.APPLICATION_JSON).content("{\"confirmed\":true}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value(206006));
    }

    @Test
    void shouldMapChangedOrUnavailableActionToFixedSafeErrors() throws Exception {
        when(confirmationService.confirm(eq("changed"), eq(true), anyString()))
                .thenReturn(new AgentConfirmationResult(
                        action(), AgentConfirmationValidationFailure.PARAMETERS_CHANGED, false));
        when(confirmationService.confirm(eq("unavailable"), eq(true), anyString()))
                .thenThrow(new BusinessException(AgentErrorCode.AGENT_RESOURCE_NOT_FOUND, "操作不可用或已失效"));

        mockMvc.perform(post("/api/v1/agent/actions/changed/confirm")
                .contentType(MediaType.APPLICATION_JSON).content("{\"confirmed\":true}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value(206004));
        mockMvc.perform(post("/api/v1/agent/actions/unavailable/confirm")
                .contentType(MediaType.APPLICATION_JSON).content("{\"confirmed\":true}"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value(206005))
                .andExpect(jsonPath("$.message").value("操作不可用或已失效"));
    }

    @Test
    void shouldMapSessionManagementAndCancelResponses() throws Exception {
        OffsetDateTime created = OffsetDateTime.parse("2026-08-05T10:00:00+08:00");
        when(runtimeService.createMySession()).thenReturn(new AgentInteractionRuntimeService.SessionView(
                "session-1", null, "ACTIVE", created, created));
        when(runtimeService.listMySessions(1, 20)).thenReturn(new PageResult<>(1L, 1, 20,
                List.of(new AgentInteractionRuntimeService.SessionView(
                        "session-1", null, "ACTIVE", created, created))));
        when(runtimeService.listMySessionMessages("session-1", 1, 20)).thenReturn(new PageResult<>(1L, 1, 20,
                List.of(new AgentInteractionRuntimeService.MessageView("message-1", "ASSISTANT", "TEXT", "已完成",
                        "52b810c5-4b03-4a41-9c36-07372f1a6f59", new ObjectMapper().readTree("{}"), "COMPLETED",
                        created, created))));
        when(runtimeService.clearMySession("session-1"))
                .thenReturn(new AgentInteractionRuntimeService.ClearSessionView("session-1", true));
        when(runtimeService.clearMySessions())
                .thenReturn(new AgentInteractionRuntimeService.BulkClearSessionView(1, 2));
        when(runtimeService.cancelMyRun("run-1"))
                .thenReturn(new AgentInteractionRuntimeService.CancelRunView("run-1", "CANCELLED", created));

        mockMvc.perform(post("/api/v1/agent/sessions"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.sessionId").value("session-1"))
                .andExpect(jsonPath("$.data.summary").hasJsonPath())
                .andExpect(jsonPath("$.data.summary").value(nullValue()));
        mockMvc.perform(get("/api/v1/agent/sessions"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.total").value(1))
                .andExpect(jsonPath("$.data.records[0].sessionId").value("session-1"))
                .andExpect(jsonPath("$.data.records[0].summary").hasJsonPath())
                .andExpect(jsonPath("$.data.records[0].summary").value(nullValue()));
        mockMvc.perform(get("/api/v1/agent/sessions/session-1/messages"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.records[0].messageId").value("message-1"))
                .andExpect(jsonPath("$.data.records[0].runId").value("52b810c5-4b03-4a41-9c36-07372f1a6f59"));
        mockMvc.perform(delete("/api/v1/agent/sessions/session-1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.cleared").value(true));
        mockMvc.perform(delete("/api/v1/agent/sessions"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.clearedCount").value(1))
                .andExpect(jsonPath("$.data.skippedCount").value(2));
        mockMvc.perform(post("/api/v1/agent/runs/run-1/cancel"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("CANCELLED"));
    }

    @Test
    void shouldRejectStreamRequestWithoutEntryContext() throws Exception {
        mockMvc.perform(post("/api/v1/agent/sessions/session-1/messages/stream")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"clientRequestId":"4fc7ae0d-1c05-4bc1-9ad8-c84b1c706925",
                         "content":"推荐电影","context":{}}
                        """))
                .andExpect(status().isBadRequest());
    }

    @Test
    void shouldRejectNonUuidClientRequestId() throws Exception {
        mockMvc.perform(post("/api/v1/agent/sessions/session-1/messages/stream")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"clientRequestId":"request-1","content":"推荐电影",
                         "context":{"entry":"workspace"}}
                        """))
                .andExpect(status().isBadRequest());
    }

    @Test
    void shouldWritePersistedEventWithStableSseFields() throws Exception {
        ObjectMapper objectMapper = new ObjectMapper();
        var started = new AgentInteractionRuntimeService.StartedSubmission(null,
                new AgentInteractionRuntimeService.StreamView("session-1", "run-1", false, 7L,
                        List.of(new AgentInteractionRuntimeService.EventView("7", "session-1", "run-1",
                                null, null, null, "message.start", "已接收消息",
                                objectMapper.readTree("{\"phase\":\"accepted\"}"),
                                OffsetDateTime.parse("2026-08-05T10:00:00+08:00")))));
        when(runtimeService.beginConversationAndReplay("session-1", "4fc7ae0d-1c05-4bc1-9ad8-c84b1c706925", "推荐电影",
                "workspace", 0L)).thenReturn(started);
        when(runtimeService.completeConversationAndReplay(eq(started), eq(7L), org.mockito.ArgumentMatchers.any()))
                .thenReturn(new AgentInteractionRuntimeService.StreamView("session-1", "run-1", false, 8L,
                        List.of(new AgentInteractionRuntimeService.EventView("8", "session-1", "run-1",
                                "plan-1", 1, "rank-\"movie", "step.complete", "步骤已完成",
                                objectMapper.readTree("{\"nodeId\":\"rank-\\\"movie\"}"),
                                OffsetDateTime.parse("2026-08-05T10:00:01+08:00")))));

        MvcResult result = mockMvc.perform(post("/api/v1/agent/sessions/session-1/messages/stream")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"clientRequestId":"4fc7ae0d-1c05-4bc1-9ad8-c84b1c706925",
                         "content":"推荐电影","context":{"entry":"workspace"}}
                        """))
                .andExpect(request().asyncStarted())
                .andReturn();
        result.getAsyncResult();

        org.assertj.core.api.Assertions.assertThat(result.getResponse().getContentAsString())
                .contains("id:7")
                .contains("event:message.start")
                .contains("id:8")
                .contains("event:step.complete")
                .contains("\"nodeId\":\"rank-\\\"movie\"");
        org.mockito.InOrder calls = Mockito.inOrder(runtimeService);
        calls.verify(runtimeService).beginConversationAndReplay("session-1", "4fc7ae0d-1c05-4bc1-9ad8-c84b1c706925",
                "推荐电影", "workspace", 0L);
        calls.verify(runtimeService).completeConversationAndReplay(eq(started), eq(7L),
                org.mockito.ArgumentMatchers.any());
    }

    @Test
    void shouldReplaySafeFailureEventsWhenSubmissionFailureWasPersisted() throws Exception {
        ObjectMapper objectMapper = new ObjectMapper();
        var started = new AgentInteractionRuntimeService.StartedSubmission(null,
                new AgentInteractionRuntimeService.StreamView("session-1", "run-1", false, 7L,
                        List.of(new AgentInteractionRuntimeService.EventView("7", "session-1", "run-1",
                                null, null, null, "message.start", "已接收消息",
                                objectMapper.readTree("{\"phase\":\"accepted\"}"),
                                OffsetDateTime.parse("2026-08-05T10:00:00+08:00")))));
        when(runtimeService.beginConversationAndReplay("session-1", "4fc7ae0d-1c05-4bc1-9ad8-c84b1c706925", "推荐电影",
                "workspace", 0L)).thenReturn(started);
        when(runtimeService.completeConversationAndReplay(eq(started), eq(7L), org.mockito.ArgumentMatchers.any()))
                .thenThrow(new AgentFailurePersistedException());
        var failedReplay = new AgentInteractionRuntimeService.StreamView("session-1", "run-1", false, 9L, List.of(
                        new AgentInteractionRuntimeService.EventView("8", "session-1", "run-1", null, null, null,
                                "message.error", "本次请求未完成", objectMapper.readTree("{\"reason\":\"RUN_FAILED\"}"),
                                OffsetDateTime.parse("2026-08-05T10:00:01+08:00")),
                        new AgentInteractionRuntimeService.EventView("9", "session-1", "run-1", null, null, null,
                                "run.complete", "运行已结束", objectMapper.readTree("{\"status\":\"FAILED\"}"),
                                OffsetDateTime.parse("2026-08-05T10:00:02+08:00"))));
        when(runtimeService.replayPersistedEvents("session-1", 7L)).thenReturn(failedReplay);

        MvcResult result = mockMvc.perform(post("/api/v1/agent/sessions/session-1/messages/stream")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"clientRequestId":"4fc7ae0d-1c05-4bc1-9ad8-c84b1c706925",
                         "content":"推荐电影","context":{"entry":"workspace"}}
                        """))
                .andExpect(request().asyncStarted())
                .andReturn();
        result.getAsyncResult();

        org.assertj.core.api.Assertions.assertThat(result.getResponse().getContentAsString())
                .contains("id:8")
                .contains("event:message.error")
                .contains("id:9")
                .contains("event:run.complete")
                .doesNotContain("数据库故障详情");
        verify(runtimeService).replayPersistedEvents("session-1", 7L);
    }

    private static AgentConfirmationAction action() {
        LocalDateTime now = LocalDateTime.of(2026, 8, 5, 10, 0);
        return AgentConfirmationAction.pending(
                1L, "action-1", 9L, 10L, 11L, "run-1", "plan-1", 2, "confirm-order",
                new ConfirmedOrderCommand("createOrder", "70001", List.of("2", "4")), now.plusMinutes(5), now);
    }

    private static AgentConfirmationAction executingAction() {
        return action().claim(AgentActionWriteIdentifiers.forAction("action-1"), LocalDateTime.of(2026, 8, 5, 10, 1));
    }
}
