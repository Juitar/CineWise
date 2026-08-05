package com.miaoyu.ticket.agent;

import static org.mockito.Mockito.when;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.request;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.miaoyu.ticket.agent.api.AgentController;
import com.miaoyu.ticket.agent.application.AgentInteractionRuntimeService;
import java.time.OffsetDateTime;
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

/** Controller 只映射应用门面输出，不泄露内部运行 ID 或持久化对象。 */
class AgentControllerTest {
    private AgentInteractionRuntimeService runtimeService;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        runtimeService = Mockito.mock(AgentInteractionRuntimeService.class);
        Executor directExecutor = Runnable::run;
        TaskScheduler taskScheduler = Mockito.mock(TaskScheduler.class);
        @SuppressWarnings("unchecked")
        ObjectProvider<TaskScheduler> taskSchedulerProvider = Mockito.mock(ObjectProvider.class);
        when(taskSchedulerProvider.getIfAvailable()).thenReturn(taskScheduler);
        mockMvc = MockMvcBuilders.standaloneSetup(
                new AgentController(runtimeService, directExecutor, taskSchedulerProvider, new ObjectMapper())).build();
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
        when(runtimeService.submitAndReplay("session-1", "4fc7ae0d-1c05-4bc1-9ad8-c84b1c706925", "推荐电影",
                "workspace", 0L)).thenReturn(new AgentInteractionRuntimeService.StreamView("session-1", "run-1",
                        false, 7L, List.of(new AgentInteractionRuntimeService.EventView("7", "session-1", "run-1",
                                1, "rank-\"movie", "step.complete", "步骤已完成",
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
                .contains("event:step.complete")
                .contains("\"nodeId\":\"rank-\\\"movie\"");
        verify(runtimeService).submitAndReplay("session-1", "4fc7ae0d-1c05-4bc1-9ad8-c84b1c706925", "推荐电影",
                "workspace", 0L);
    }
}
