package com.miaoyu.ticket.content.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.InputStream;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

@ActiveProfiles("test")
@SpringBootTest
@AutoConfigureMockMvc
class ContentCityResolutionControllerIntegrationTest {

    private static final String FIXTURE = "/fixtures/content/c/city-resolution-resolved.json";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    /** C 只接收标准城市名，候选和 Provider 标识都不允许出现在 JSON 中。 */
    @Test
    void givenTemporaryChangshaLocation_whenResolve_thenItMatchesCFixtureAndHidesProviderFields() throws Exception {
        JsonNode fixture = fixture();

        mockMvc.perform(post("/api/v1/content/cities/resolve")
                        .with(user("content-reader").roles("USER"))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"locationText\":\"湖南省长沙市岳麓区\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(fixture.required("code").asInt()))
                .andExpect(jsonPath("$.data.status").value(fixture.required("data").required("status").asText()))
                .andExpect(jsonPath("$.data.cityName").value(fixture.required("data").required("cityName").asText()))
                .andExpect(jsonPath("$.data.providerCityId").doesNotExist())
                .andExpect(jsonPath("$.data.ci").doesNotExist())
                .andExpect(jsonPath("$.data.candidates").doesNotExist());
    }

    @Test
    void givenNoOrMultipleMatchedCity_whenResolve_thenItReturnsOnlyControlledStatuses() throws Exception {
        mockMvc.perform(resolve("未知地点"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("UNRECOGNIZED"))
                .andExpect(jsonPath("$.data.cityName").isEmpty());

        mockMvc.perform(resolve("长沙到杭州"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("SELECTION_REQUIRED"))
                .andExpect(jsonPath("$.data.cityName").isEmpty());
    }

    @Test
    void shouldPublishTheControlledCityResolutionSchemaInOpenApi() throws Exception {
        mockMvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.paths['/api/v1/content/cities/resolve'].post").exists())
                .andExpect(jsonPath("$.components.schemas.CityResolutionResponse.properties.status").exists())
                .andExpect(jsonPath("$.components.schemas.CityResolutionResponse.properties.cityName").exists())
                .andExpect(jsonPath(
                        "$.components.schemas.CityResolutionResponse.properties.providerCityId").doesNotExist());
    }

    /** 当前安全规则要求登录和 CSRF；该测试复用 C 已有 Cookie/CSRF 请求方式。 */
    private org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder resolve(String locationText) {
        return post("/api/v1/content/cities/resolve")
                .with(user("content-reader").roles("USER"))
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"locationText\":\"" + locationText + "\"}");
    }

    private JsonNode fixture() throws Exception {
        try (InputStream input = getClass().getResourceAsStream(FIXTURE)) {
            assertThat(input).as("夹具必须存在: %s", FIXTURE).isNotNull();
            return objectMapper.readTree(input);
        }
    }
}
