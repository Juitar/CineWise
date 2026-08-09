package com.miaoyu.ticket.travel.api;

import static org.assertj.core.api.Assertions.assertThat;
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
import com.miaoyu.ticket.geo.application.UserLocationAdapter;
import com.miaoyu.ticket.geo.domain.LocationGranularity;
import com.miaoyu.ticket.geo.domain.ResolvedGeoPoint;
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
    private final UserLocationAdapter manualPlaceLocationAdapter = mock(UserLocationAdapter.class);
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(
                        new TravelRouteController(
                                routeService, new BrowserUserLocationAdapter(), manualPlaceLocationAdapter))
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    void givenConfirmedDrivingRequest_whenPlanning_thenReturnRouteWithoutCoordinates() throws Exception {
        when(routeService.planMyRoute(eq("90001"), any())).thenReturn(route());

        mockMvc.perform(post("/api/v1/travel/tasks/90001/route")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"originType\":\"CURRENT_LOCATION\",\"longitude\":112.9388146,"
                                + "\"latitude\":28.2282085,\"travelMode\":\"DRIVING\","
                                + "\"thirdPartySharingConfirmed\":true}"))
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
                        .content("{\"originType\":\"CURRENT_LOCATION\",\"longitude\":180.0000004,\"latitude\":0,"
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

    @Test
    void givenUniqueManualAddress_whenPlanning_thenDelegateResolvedOriginWithoutReturningCoordinates()
            throws Exception {
        when(manualPlaceLocationAdapter.fromPlaceText("长沙市雨花区万家丽中路 1 号"))
                .thenReturn(new ResolvedGeoPoint(
                        new java.math.BigDecimal("112.938815"), new java.math.BigDecimal("28.228209"),
                        LocationGranularity.ADDRESS));
        when(routeService.planMyRoute(eq("90001"), any())).thenReturn(route());

        mockMvc.perform(post("/api/v1/travel/tasks/90001/route")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"originType\":\"MANUAL_PLACE\",\"placeText\":\"长沙市雨花区万家丽中路 1 号\","
                                + "\"travelMode\":\"WALKING\",\"thirdPartySharingConfirmed\":true}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.longitude").doesNotExist());
        verify(manualPlaceLocationAdapter).fromPlaceText("长沙市雨花区万家丽中路 1 号");
    }

    @Test
    void givenAmbiguousOrOverbroadManualPlace_whenPlanning_thenRejectWithoutCallingRouteProvider()
            throws Exception {
        when(manualPlaceLocationAdapter.fromPlaceText("人民广场"))
                .thenThrow(new IllegalArgumentException("ambiguous"));
        when(manualPlaceLocationAdapter.fromPlaceText("长沙市"))
                .thenReturn(new ResolvedGeoPoint(
                        new java.math.BigDecimal("112.938815"), new java.math.BigDecimal("28.228209"),
                        LocationGranularity.CITY));

        mockMvc.perform(post("/api/v1/travel/tasks/90001/route")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"originType\":\"MANUAL_PLACE\",\"placeText\":\"人民广场\","
                                + "\"travelMode\":\"WALKING\",\"thirdPartySharingConfirmed\":true}"))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value(107004));
        mockMvc.perform(post("/api/v1/travel/tasks/90001/route")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"originType\":\"MANUAL_PLACE\",\"placeText\":\"长沙市\","
                                + "\"travelMode\":\"WALKING\",\"thirdPartySharingConfirmed\":true}"))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value(107005));

        verify(routeService, never()).planMyRoute(any(), any());
    }

    @Test
    void givenMixedOriginFields_whenPlanning_thenRejectWithoutCallingAdaptersOrRouteProvider()
            throws Exception {
        mockMvc.perform(post("/api/v1/travel/tasks/90001/route")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"originType\":\"CURRENT_LOCATION\",\"longitude\":112.938814,"
                                + "\"latitude\":28.228209,\"placeText\":\"长沙\",\"travelMode\":\"WALKING\","
                                + "\"thirdPartySharingConfirmed\":true}"))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.code").value(307001));
        mockMvc.perform(post("/api/v1/travel/tasks/90001/route")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"originType\":\"MANUAL_PLACE\",\"placeText\":\"长沙市雨花区万家丽中路 1 号\","
                                + "\"longitude\":112.938814,\"travelMode\":\"WALKING\","
                                + "\"thirdPartySharingConfirmed\":true}"))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value(107004));

        verify(manualPlaceLocationAdapter, never()).fromPlaceText(any());
        verify(routeService, never()).planMyRoute(any(), any());
    }

    @Test
    void givenRouteRequest_whenRenderedForDiagnosticLogging_thenRedactPlaceAndCoordinates() {
        TravelRouteController.PlanTravelRouteRequest request = new TravelRouteController.PlanTravelRouteRequest(
                "MANUAL_PLACE",
                new java.math.BigDecimal("112.938814"),
                new java.math.BigDecimal("28.228209"),
                "长沙市雨花区万家丽中路二段8号",
                "DRIVING",
                true);

        assertThat(request)
                .hasToString("PlanTravelRouteRequest[originType=MANUAL_PLACE, location=[REDACTED], travelMode=DRIVING, "
                        + "thirdPartySharingConfirmed=true]");
    }

    private String request(boolean confirmed) {
        return "{\"originType\":\"CURRENT_LOCATION\",\"longitude\":112.9388146,\"latitude\":28.2282085,"
                + "\"travelMode\":\"WALKING\",\"thirdPartySharingConfirmed\":" + confirmed + "}";
    }

    private BasicRouteResult route() {
        OffsetDateTime now = OffsetDateTime.parse("2026-08-08T10:00:00+08:00");
        return new BasicRouteResult("AMAP", "DRIVING", 20, now.plusMinutes(40), "AMAP_ROUTE", now,
                now.plusMinutes(15), false, false, null);
    }
}
