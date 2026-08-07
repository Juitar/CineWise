package com.miaoyu.ticket.travel.api;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.miaoyu.ticket.common.error.BusinessException;
import com.miaoyu.ticket.common.error.GlobalExceptionHandler;
import com.miaoyu.ticket.geo.application.BrowserUserLocationAdapter;
import com.miaoyu.ticket.travel.application.BasicRouteResult;
import com.miaoyu.ticket.travel.application.BasicRouteService;
import com.miaoyu.ticket.travel.application.TravelErrorCode;
import java.time.OffsetDateTime;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

/** 路线 HTTP 契约覆盖认证后的任务归属、共享确认和精确坐标错误边界。 */
class TravelRouteControllerTest {

    private final BasicRouteService routeService = mock(BasicRouteService.class);
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(
                        new TravelRouteController(routeService, new BrowserUserLocationAdapter()))
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    void givenConfirmedDrivingRequest_whenPlanning_thenReturnRouteWithoutCoordinates() throws Exception {
        when(routeService.planMyRoute(eq("90001"), any())).thenReturn(route());

        mockMvc.perform(post("/api/v1/travel/tasks/90001/route")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"longitude\":112.9388146,\"latitude\":28.2282085,"
                                + "\"travelMode\":\"DRIVING\",\"thirdPartySharingConfirmed\":true}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.travelMode").value("DRIVING"))
                .andExpect(jsonPath("$.data.longitude").doesNotExist())
                .andExpect(jsonPath("$.data.latitude").doesNotExist());
        verify(routeService).planMyRoute(eq("90001"), any());
    }

    @Test
    void givenRawCoordinateOutsideRange_whenPlanning_thenReturnRouteUnavailableWithoutCallingService()
            throws Exception {
        mockMvc.perform(post("/api/v1/travel/tasks/90001/route")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"longitude\":180.0000004,\"latitude\":0,"
                                + "\"travelMode\":\"DRIVING\",\"thirdPartySharingConfirmed\":true}"))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.code").value(307001));
        verify(routeService, never()).planMyRoute(any(), any());
    }

    @Test
    void givenTaskNotFoundOrSharingNotConfirmed_whenPlanning_thenKeepExistingErrorSemantics() throws Exception {
        when(routeService.planMyRoute(eq("missing"), any()))
                .thenThrow(new BusinessException(TravelErrorCode.TASK_NOT_FOUND));
        when(routeService.planMyRoute(eq("90001"), any()))
                .thenThrow(new BusinessException(TravelErrorCode.ROUTE_SHARING_NOT_CONFIRMED));

        mockMvc.perform(post("/api/v1/travel/tasks/missing/route")
                        .contentType(MediaType.APPLICATION_JSON).content(request(true)))
                .andExpect(status().isNotFound()).andExpect(jsonPath("$.code").value(207001));
        mockMvc.perform(post("/api/v1/travel/tasks/90001/route")
                        .contentType(MediaType.APPLICATION_JSON).content(request(false)))
                .andExpect(status().isUnprocessableEntity()).andExpect(jsonPath("$.code").value(107002));
    }

    private String request(boolean confirmed) {
        return "{\"longitude\":112.9388146,\"latitude\":28.2282085,"
                + "\"travelMode\":\"WALKING\",\"thirdPartySharingConfirmed\":" + confirmed + "}";
    }

    private BasicRouteResult route() {
        OffsetDateTime now = OffsetDateTime.parse("2026-08-08T10:00:00+08:00");
        return new BasicRouteResult("AMAP", "DRIVING", 20, now.plusMinutes(40), "AMAP_ROUTE", now,
                now.plusMinutes(15), false, false, null);
    }
}
