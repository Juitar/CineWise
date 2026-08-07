package com.miaoyu.ticket.agent;

import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.miaoyu.ticket.agent.application.AgentErrorCode;
import com.miaoyu.ticket.agent.application.audit.AdminAgentRunPageView;
import com.miaoyu.ticket.agent.application.audit.AdminAgentRunQueryService;
import com.miaoyu.ticket.agent.application.audit.AdminAgentRunView;
import com.miaoyu.ticket.auth.application.CurrentUser;
import com.miaoyu.ticket.auth.application.RoleCode;
import com.miaoyu.ticket.common.error.BusinessException;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

@ActiveProfiles("test")
@SpringBootTest
@AutoConfigureMockMvc
class AdminAgentRunControllerSecurityIntegrationTest {
    @Autowired private MockMvc mockMvc;
    @MockBean private AdminAgentRunQueryService service;

    @Test
    void shouldAllowAdminListAndDetailButRejectUser() throws Exception {
        when(service.queryRuns(org.mockito.ArgumentMatchers.any())).thenReturn(new AdminAgentRunPageView(1, 1, 20,
                List.of(view())));
        when(service.queryRun("run-1")).thenReturn(view());

        mockMvc.perform(get("/api/v1/admin/agent-runs").with(authentication(auth(RoleCode.ADMIN))))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.records[0].runId").value("run-1"));
        mockMvc.perform(get("/api/v1/admin/agent-runs/run-1").with(authentication(auth(RoleCode.ADMIN))))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.nodes[0].targetName").value("queryShows"));
        mockMvc.perform(get("/api/v1/admin/agent-runs").with(authentication(auth(RoleCode.USER))))
                .andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value(201007));
        mockMvc.perform(get("/api/v1/admin/agent-runs/run-1").with(authentication(auth(RoleCode.USER))))
                .andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value(201007));
    }

    @Test
    void shouldReturnExisting404ForMissingRun() throws Exception {
        when(service.queryRun("missing")).thenThrow(new BusinessException(AgentErrorCode.AGENT_RESOURCE_NOT_FOUND));
        mockMvc.perform(get("/api/v1/admin/agent-runs/missing").with(authentication(auth(RoleCode.ADMIN))))
                .andExpect(status().isNotFound()).andExpect(jsonPath("$.code").value(206005));
    }

    private static UsernamePasswordAuthenticationToken auth(RoleCode role) {
        return UsernamePasswordAuthenticationToken.authenticated(new CurrentUser(1L, role, 0L), "N/A",
                Set.of(new SimpleGrantedAuthority("ROLE_" + role.name())));
    }

    private static AdminAgentRunView view() {
        LocalDateTime now = LocalDateTime.of(2026, 8, 5, 19, 20);
        return new AdminAgentRunView("run-1", "session-1", "u***@example.com", "FAILED", "plan-1", 1,
                1, 0, 1, now, now.plusSeconds(8), 8000L, null, "工具查询失败", List.of(
                        new AdminAgentRunView.NodeView("node-1", "CALL_TOOL", "queryShows", "FAILED", 1, now,
                                now.plusSeconds(8), 8000L, "FAILED", null, "工具查询失败", null)));
    }
}
