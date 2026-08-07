package com.miaoyu.ticket.agent;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.miaoyu.ticket.agent.application.AgentErrorCode;
import com.miaoyu.ticket.agent.application.audit.AdminAgentRunListQuery;
import com.miaoyu.ticket.agent.application.audit.AdminAgentRunQueryRepository;
import com.miaoyu.ticket.agent.application.audit.AdminAgentRunQueryService;
import com.miaoyu.ticket.agent.application.persistence.AgentRunStepRepository;
import com.miaoyu.ticket.agent.application.persistence.AgentRuntimeEventRepository;
import com.miaoyu.ticket.agent.application.persistence.AgentSessionRepository;
import com.miaoyu.ticket.agent.domain.persistence.AgentEventType;
import com.miaoyu.ticket.agent.domain.persistence.AgentRequestHash;
import com.miaoyu.ticket.agent.domain.persistence.AgentRun;
import com.miaoyu.ticket.agent.domain.persistence.AgentRunStatus;
import com.miaoyu.ticket.agent.domain.persistence.AgentRuntimeEvent;
import com.miaoyu.ticket.agent.domain.persistence.AgentStoredJson;
import com.miaoyu.ticket.auth.application.CurrentUser;
import com.miaoyu.ticket.auth.application.CurrentUserAccessor;
import com.miaoyu.ticket.auth.application.RoleCode;
import com.miaoyu.ticket.auth.application.UserAdminQueryPort;
import com.miaoyu.ticket.common.config.ApiProperties;
import com.miaoyu.ticket.common.error.BusinessException;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class AdminAgentRunQueryServiceTest {
    @Test
    void shouldReturnAdminPageAndNeverExposeEventPayload() {
        CurrentUserAccessor users = mock(CurrentUserAccessor.class);
        UserAdminQueryPort directory = mock(UserAdminQueryPort.class);
        AdminAgentRunQueryRepository runs = mock(AdminAgentRunQueryRepository.class);
        AgentRunStepRepository steps = mock(AgentRunStepRepository.class);
        AgentSessionRepository sessions = mock(AgentSessionRepository.class);
        AgentRuntimeEventRepository events = mock(AgentRuntimeEventRepository.class);
        when(users.requireCurrentUser()).thenReturn(new CurrentUser(9L, RoleCode.ADMIN, 0L));
        when(runs.count(any())).thenReturn(1L);
        when(runs.findPage(any())).thenReturn(List.of(run()));
        when(directory.findByUserIds(any())).thenReturn(Map.of(7L,
                new UserAdminQueryPort.UserAdminSummary(7L, "u***@example.com")));

        var result = service(users, directory, runs, steps, sessions, events)
                .queryRuns(new AdminAgentRunListQuery("FAILED", null, null, null, 1, 20));

        assertThat(result.total()).isEqualTo(1);
        assertThat(result.records()).singleElement().satisfies(view -> {
            assertThat(view.runId()).isEqualTo("run-1");
            assertThat(view.userDisplay()).isEqualTo("u***@example.com");
            assertThat(view.errorSummary()).isEqualTo("工具查询失败");
            assertThat(view.toString()).doesNotContain("tool-secret", "latitude", "jwt");
        });
    }

    @Test
    void shouldRejectUserBeforeAnyRead() {
        CurrentUserAccessor users = mock(CurrentUserAccessor.class);
        UserAdminQueryPort directory = mock(UserAdminQueryPort.class);
        AdminAgentRunQueryRepository runs = mock(AdminAgentRunQueryRepository.class);
        AgentRunStepRepository steps = mock(AgentRunStepRepository.class);
        AgentSessionRepository sessions = mock(AgentSessionRepository.class);
        AgentRuntimeEventRepository events = mock(AgentRuntimeEventRepository.class);
        when(users.requireCurrentUser()).thenReturn(new CurrentUser(8L, RoleCode.USER, 0L));

        assertThatThrownBy(() -> service(users, directory, runs, steps, sessions, events)
                .queryRuns(new AdminAgentRunListQuery(null, null, null, null, 1, 20)))
                .isInstanceOf(BusinessException.class)
                .extracting(error -> ((BusinessException) error).getErrorCode().code()).isEqualTo(100403);
        verifyNoInteractions(directory, runs, steps, sessions, events);
    }

    @Test
    void shouldUseExisting404ForMissingRun() {
        CurrentUserAccessor users = mock(CurrentUserAccessor.class);
        UserAdminQueryPort directory = mock(UserAdminQueryPort.class);
        AdminAgentRunQueryRepository runs = mock(AdminAgentRunQueryRepository.class);
        AgentRunStepRepository steps = mock(AgentRunStepRepository.class);
        AgentSessionRepository sessions = mock(AgentSessionRepository.class);
        AgentRuntimeEventRepository events = mock(AgentRuntimeEventRepository.class);
        when(users.requireCurrentUser()).thenReturn(new CurrentUser(9L, RoleCode.ADMIN, 0L));
        when(runs.findByRunId("missing")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service(users, directory, runs, steps, sessions, events).queryRun("missing"))
                .isInstanceOfSatisfying(BusinessException.class,
                        error -> assertThat(error.getErrorCode()).isEqualTo(AgentErrorCode.AGENT_RESOURCE_NOT_FOUND));
    }

    private static AdminAgentRunQueryService service(CurrentUserAccessor users, UserAdminQueryPort directory,
            AdminAgentRunQueryRepository runs, AgentRunStepRepository steps, AgentSessionRepository sessions,
            AgentRuntimeEventRepository events) {
        return new AdminAgentRunQueryService(users, directory, runs, steps, sessions, events,
                new ApiProperties(1, 20, 100), new ObjectMapper());
    }

    private static AgentRun run() {
        LocalDateTime now = LocalDateTime.of(2026, 8, 5, 19, 20);
        return new AgentRun(1L, "run-1", 2L, 7L, "request-1", new AgentRequestHash("v1",
                "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa"), "plan-1", 1,
                AgentRunStatus.FAILED, "trace", now, now.plusSeconds(8), 0L, now, now, now.plusDays(30));
    }
}
