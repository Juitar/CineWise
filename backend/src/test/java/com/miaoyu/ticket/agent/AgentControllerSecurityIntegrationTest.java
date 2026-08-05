package com.miaoyu.ticket.agent;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

/** Agent 不自行解析身份；未登录响应完全复用 C 的安全过滤器。 */
@ActiveProfiles("test")
@SpringBootTest
@AutoConfigureMockMvc
class AgentControllerSecurityIntegrationTest {
    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void shouldKeepCSessionInvalidResponseForUnauthenticatedRunAndPostSse() throws Exception {
        mockMvc.perform(get("/api/v1/agent/runs/run-other-user"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value(201006));

        MvcResult csrfResult = mockMvc.perform(get("/api/v1/auth/csrf"))
                .andExpect(status().isOk())
                .andReturn();
        JsonNode csrf = objectMapper.readTree(csrfResult.getResponse().getContentAsByteArray());
        Cookie csrfCookie = csrfResult.getResponse().getCookie("cinewise_csrf");
        assertThat(csrfCookie).isNotNull();

        mockMvc.perform(post("/api/v1/agent/sessions/session-1/messages/stream")
                        .cookie(csrfCookie)
                        .header("X-XSRF-TOKEN", csrf.at("/data/token").asText())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"clientRequestId":"4fc7ae0d-1c05-4bc1-9ad8-c84b1c706925",
                                 "content":"推荐电影","context":{"entry":"workspace"}}
                                """))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value(201006));
    }
}
