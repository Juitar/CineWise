package com.miaoyu.ticket.travel.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.miaoyu.ticket.common.error.CommonErrorCode;
import com.miaoyu.ticket.travel.application.TravelErrorCode;
import com.miaoyu.ticket.travel.application.TravelTaskQueryService;
import com.miaoyu.ticket.travel.domain.TravelTaskStatus;
import java.io.InputStream;
import java.time.OffsetDateTime;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

/** 固定夹具必须与 Controller 的类型化响应一致，防止 C 页面按夹具开发后在线上遇到另一种结构。 */
class TravelTaskControllerContractTest {

    private final ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();
    private final TravelTaskQueryService queryService = mock(TravelTaskQueryService.class);
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(new TravelTaskController(queryService, objectMapper)).build();
    }

    @Test
    void givenNormalWeatherFixture_whenReadingAdvice_thenReturnTypedWeatherAndAdvice() throws Exception {
        assertAdviceFixture("advice-weather-normal.json", summary(
                "90001", TravelTaskStatus.READY, true,
                "{\"area\":\"岳麓区\",\"condition\":\"多云 29℃\",\"risk\":\"关注短时降雨\"}",
                "{\"weatherAdvice\":\"关注短时降雨\",\"transportAdvice\":\"请预留充足时间，优先选择公共交通并提前到场\"}",
                "AMAP_WEATHER", false, false, null));
    }

    @Test
    void givenWeatherUnavailableFixture_whenReadingAdvice_thenOnlyReturnTransportAdvice() throws Exception {
        assertAdviceFixture("advice-weather-unavailable.json", summary(
                "90001", TravelTaskStatus.READY, true, null,
                "{\"weatherAdvice\":\"天气暂不可用\",\"transportAdvice\":\"请预留充足时间，优先选择公共交通并提前到场\"}",
                "UNAVAILABLE", false, true, "NO_WEATHER"));
    }

    @Test
    void givenDemoWeatherFixture_whenReadingAdvice_thenReturnWeatherAndDegradedAdvice() throws Exception {
        assertAdviceFixture("advice-weather-demo.json", summary(
                "90001", TravelTaskStatus.READY, true,
                "{\"area\":\"岳麓区\",\"condition\":\"多云（演示）\",\"risk\":\"演示数据，请出发前自行确认\"}",
                "{\"weatherAdvice\":\"演示数据，请出发前自行确认\",\"transportAdvice\":\"请预留充足时间，优先选择公共交通并提前到场\"}",
                "DEMO_WEATHER_V1", false, true, "DEMO"));
    }

    @Test
    void givenExpiredFixture_whenReadingAdvice_thenKeepTypedAdviceAndExpiredFlag() throws Exception {
        assertAdviceFixture("advice-expired.json", summary(
                "90001", TravelTaskStatus.CANCELLED, true, null,
                "{\"transportAdvice\":\"请预留充足时间，优先选择公共交通并提前到场\"}",
                "DEMO_WEATHER_V1", true, true, "DEMO"));
    }

    @Test
    void givenNotGeneratedFixture_whenReadingAdvice_thenReturnEmptyTypedAdvice() throws Exception {
        assertAdviceFixture("advice-not-generated.json", summary(
                "90003", TravelTaskStatus.PENDING, false, null, null, null, false, false, null));
    }

    @Test
    void givenReminderUpdateSucceedsButDetailDependencyIsUnavailable_whenUpdating_thenReturnCommittedTask()
            throws Exception {
        TravelTaskQueryService.TravelTaskView updated = new TravelTaskQueryService.TravelTaskView(
                "90001", "80001", TravelTaskStatus.READY,
                OffsetDateTime.parse("2026-08-05T17:00:00+08:00"), 2L);
        when(queryService.updateMyReminder(eq("90001"), any(), eq(1L))).thenReturn(updated);

        mockMvc.perform(put("/api/v1/travel/tasks/90001/reminder")
                        .header("If-Match", "1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"triggerAt\":\"2026-08-05T17:00:00+08:00\",\"version\":1}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.taskId").value("90001"))
                .andExpect(jsonPath("$.data.orderId").value("80001"))
                .andExpect(jsonPath("$.data.version").value(2));
        // 更新成功后不能再读取详情摘要，否则依赖短暂不可用会把已提交写入误报为失败。
        verify(queryService, never()).getMyTaskDetails("90001");
    }

    @Test
    void givenReminderBrowserFixtures_whenCheckingContract_thenKeepKnownSuccessAndErrorCodes() throws Exception {
        JsonNode manifest = fixtureJson("reminder-update-fixtures.json");
        JsonNode request = manifest.required("updateRequest");
        assertThat(request.required("method").asText()).isEqualTo("PUT");
        assertThat(request.required("headers").required("If-Match").asText()).isEqualTo("\"1\"");
        assertThat(request.required("body").required("version").asLong()).isEqualTo(1L);

        JsonNode success = fixtureJson("reminder-update-success.json");
        assertThat(success.required("httpStatus").asInt()).isEqualTo(200);
        assertThat(success.required("data").required("version").asLong()).isEqualTo(2L);

        assertFixtureError("reminder-update-header-body-version-conflict.json", 409, CommonErrorCode.CONFLICT.code());
        assertFixtureError("reminder-update-version-conflict.json", 409, TravelErrorCode.TASK_VERSION_CONFLICT.code());
        assertFixtureError("reminder-update-not-found.json", 404, TravelErrorCode.TASK_NOT_FOUND.code());
        assertFixtureError("reminder-update-cancelled.json", 409, TravelErrorCode.TASK_CANCELLED.code());
        JsonNode recovery = fixtureJson("reminder-update-timeout-get-recovery.json");
        assertThat(recovery.required("getResponse").required("data").required("version").asLong()).isEqualTo(2L);
    }

    private void assertAdviceFixture(String fixtureName, TravelTaskQueryService.TravelAdviceSummary summary)
            throws Exception {
        when(queryService.getMyAdviceSummary(summary.taskId())).thenReturn(summary);
        JsonNode expected = fixtureData(fixtureName);
        String response = mockMvc.perform(get("/api/v1/travel/tasks/{taskId}/advice", summary.taskId()))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        JsonNode actual = objectMapper.readTree(response).required("data");
        assertThat(actual.required("available")).isEqualTo(expected.required("available"));
        assertThat(actual.required("taskId")).isEqualTo(expected.required("taskId"));
        assertThat(actual.required("taskStatus")).isEqualTo(expected.required("taskStatus"));
        assertThat(actual.required("weather")).isEqualTo(expected.required("weather"));
        assertThat(actual.required("advice")).isEqualTo(expected.required("advice"));
        assertThat(actual.required("source")).isEqualTo(expected.required("source"));
        assertThat(actual.required("isExpired")).isEqualTo(expected.required("isExpired"));
        assertThat(actual.required("degraded")).isEqualTo(expected.required("degraded"));
        assertThat(actual.required("fallbackType")).isEqualTo(expected.required("fallbackType"));
    }

    private JsonNode fixtureData(String fixtureName) throws Exception {
        return fixtureJson(fixtureName).required("data");
    }

    private JsonNode fixtureJson(String fixtureName) throws Exception {
        try (InputStream input = new ClassPathResource("fixtures/travel/c/" + fixtureName).getInputStream()) {
            return objectMapper.readTree(input);
        }
    }

    private void assertFixtureError(String fixtureName, int statusCode, int errorCode) throws Exception {
        JsonNode error = fixtureJson(fixtureName);
        assertThat(error.required("httpStatus").asInt()).isEqualTo(statusCode);
        assertThat(error.required("code").asInt()).isEqualTo(errorCode);
    }

    private TravelTaskQueryService.TravelAdviceSummary summary(
            String taskId, TravelTaskStatus status, boolean available, String weatherJson, String adviceJson,
            String source, boolean expired, boolean degraded, String fallbackType) {
        OffsetDateTime dataAt = OffsetDateTime.parse("2026-08-05T16:00:00+08:00");
        return new TravelTaskQueryService.TravelAdviceSummary(taskId, status, expired, available,
                weatherJson, adviceJson, source, dataAt.toLocalDateTime(), dataAt.plusHours(1).toLocalDateTime(),
                degraded, fallbackType);
    }
}
