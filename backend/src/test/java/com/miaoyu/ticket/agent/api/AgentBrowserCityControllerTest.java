package com.miaoyu.ticket.agent.api;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.miaoyu.ticket.agent.application.location.BrowserCityResolutionService;
import com.miaoyu.ticket.common.error.GlobalExceptionHandler;
import java.math.BigDecimal;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

class AgentBrowserCityControllerTest {

    private final BrowserCityResolutionService cityResolutionService = mock(BrowserCityResolutionService.class);
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(new AgentBrowserCityController(cityResolutionService))
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    void shouldReturnOnlyCityName() throws Exception {
        when(cityResolutionService.resolveCurrentUserCity(any(BigDecimal.class), any(BigDecimal.class)))
                .thenReturn("长沙市");

        mockMvc.perform(post("/api/v1/agent/location/city")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"longitude\":112.938815,\"latitude\":28.228209}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.city").value("长沙市"))
                .andExpect(jsonPath("$.data.longitude").doesNotExist())
                .andExpect(jsonPath("$.data.latitude").doesNotExist());

        verify(cityResolutionService).resolveCurrentUserCity(new BigDecimal("112.938815"), new BigDecimal("28.228209"));
    }

    @Test
    void shouldRejectOverPreciseCoordinateBeforeCallingApplicationService() throws Exception {
        mockMvc.perform(post("/api/v1/agent/location/city")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"longitude\":112.9388151234567,\"latitude\":28.228209}"))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(cityResolutionService);
    }
}
