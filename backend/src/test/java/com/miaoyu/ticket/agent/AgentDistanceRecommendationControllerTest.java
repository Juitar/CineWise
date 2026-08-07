package com.miaoyu.ticket.agent;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.miaoyu.ticket.agent.api.AgentDistanceContextResponse;
import com.miaoyu.ticket.agent.api.AgentDistanceRecommendationController;
import com.miaoyu.ticket.agent.api.AgentDistanceRecommendationResultRequest;
import com.miaoyu.ticket.agent.api.AgentDistanceRunInitializeRequest;
import com.miaoyu.ticket.agent.api.AgentDistanceRunResponse;
import com.miaoyu.ticket.agent.application.AgentDistanceRecommendationApplicationService;
import com.miaoyu.ticket.common.error.GlobalExceptionHandler;
import java.time.Instant;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.http.MediaType;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

/** 距离推荐控制器只校验 C 契约并委托应用服务，不直接调用 D。 */
class AgentDistanceRecommendationControllerTest {
    private AgentDistanceRecommendationApplicationService applicationService;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        applicationService = Mockito.mock(AgentDistanceRecommendationApplicationService.class);
        ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules()
                .enable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);
        mockMvc = MockMvcBuilders.standaloneSetup(new AgentDistanceRecommendationController(applicationService))
                .setControllerAdvice(new GlobalExceptionHandler())
                .setMessageConverters(new MappingJackson2HttpMessageConverter(objectMapper))
                .build();
    }

    @Test
    void shouldInitializeWaitingRunWithoutDistanceFields() throws Exception {
        when(applicationService.initialize(eq("session-1"), Mockito.any(AgentDistanceRunInitializeRequest.class)))
                .thenReturn(new AgentDistanceRunResponse("run-1", "WAITING_LOCATION", "46"));

        mockMvc.perform(post("/api/v1/agent/sessions/session-1/distance-recommendation-runs")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"clientRequestId":"4fc7ae0d-1c05-4bc1-9ad8-c84b1c706925",
                         "content":"推荐电影","context":{"entry":"workspace"}}
                        """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.runId").value("run-1"))
                .andExpect(jsonPath("$.data.status").value("WAITING_LOCATION"))
                .andExpect(jsonPath("$.data.lastEventId").value("46"))
                .andExpect(jsonPath("$.data.distanceContextId").doesNotExist());
    }

    @Test
    void shouldRejectLocationAndUnknownFieldsAtInitialization() throws Exception {
        mockMvc.perform(post("/api/v1/agent/sessions/session-1/distance-recommendation-runs")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"clientRequestId":"4fc7ae0d-1c05-4bc1-9ad8-c84b1c706925","content":"推荐电影",
                         "context":{"entry":"workspace"},"latitude":"31.2"}
                        """))
                .andExpect(status().isBadRequest());
        verifyNoInteractions(applicationService);
    }

    @Test
    void shouldExposeContextOnlyFromDedicatedEndpoint() throws Exception {
        when(applicationService.createContext("session-1", "run-1"))
                .thenReturn(new AgentDistanceContextResponse("7d2b75e4-a18d-46d3-b327-f35dc8f37f2b",
                        Instant.parse("2026-08-07T06:05:00Z"), "NEAREST"));

        mockMvc.perform(post("/api/v1/agent/sessions/session-1/runs/run-1/distance-context"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.distanceContextId").value("7d2b75e4-a18d-46d3-b327-f35dc8f37f2b"))
                .andExpect(jsonPath("$.data.distancePreference").value("NEAREST"));
        verify(applicationService).createContext("session-1", "run-1");
    }

    @Test
    void shouldForwardOnlyApprovedLocationResult() throws Exception {
        when(applicationService.submitLocationResult(eq("run-1"),
                Mockito.any(AgentDistanceRecommendationResultRequest.class)))
                .thenReturn(new AgentDistanceRunResponse("run-1", "COMPLETED", "47"));

        mockMvc.perform(post("/api/v1/agent/runs/run-1/distance-recommendation")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"distanceContextId":"7d2b75e4-a18d-46d3-b327-f35dc8f37f2b",
                         "distancePreference":"NEAREST","locationResult":"UPLOADED"}
                        """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.runId").value("run-1"))
                .andExpect(jsonPath("$.data.lastEventId").value("47"))
                .andExpect(jsonPath("$.data.distanceContextId").doesNotExist());
    }

    @Test
    void shouldRejectInvalidResultBeforeCallingApplicationService() throws Exception {
        mockMvc.perform(post("/api/v1/agent/runs/run-1/distance-recommendation")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"distanceContextId":"not-a-uuid","distancePreference":"OTHER","locationResult":"UPLOADED"}
                        """))
                .andExpect(status().isBadRequest());
        verifyNoInteractions(applicationService);
    }

    @Test
    void shouldFindByOriginalClientRequestOnly() throws Exception {
        when(applicationService.findByClientRequest("session-1", "4fc7ae0d-1c05-4bc1-9ad8-c84b1c706925"))
                .thenReturn(new AgentDistanceRunResponse("run-1", "WAITING_LOCATION", "46"));

        mockMvc.perform(get("/api/v1/agent/sessions/session-1/runs/by-client-request/"
                + "4fc7ae0d-1c05-4bc1-9ad8-c84b1c706925"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("WAITING_LOCATION"));
        verify(applicationService).findByClientRequest("session-1", "4fc7ae0d-1c05-4bc1-9ad8-c84b1c706925");
    }
}
