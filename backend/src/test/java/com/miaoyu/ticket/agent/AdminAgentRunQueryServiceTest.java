package com.miaoyu.ticket.agent;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.miaoyu.ticket.agent.application.AgentErrorCode;
import com.miaoyu.ticket.agent.application.audit.AdminAgentRunListQuery;
import com.miaoyu.ticket.agent.application.audit.AdminAgentRunQueryRepository;
import com.miaoyu.ticket.agent.application.audit.AdminAgentRunQueryService;
import com.miaoyu.ticket.agent.application.persistence.AgentRunStepRepository;
import com.miaoyu.ticket.agent.application.persistence.AgentSessionRepository;
import com.miaoyu.ticket.agent.domain.persistence.AgentRequestHash;
import com.miaoyu.ticket.agent.domain.persistence.AgentRun;
import com.miaoyu.ticket.agent.domain.persistence.AgentRunStep;
import com.miaoyu.ticket.agent.domain.persistence.AgentRunStatus;
import com.miaoyu.ticket.agent.domain.persistence.AgentStoredJson;
import com.miaoyu.ticket.agent.domain.plan.FailurePolicy;
import com.miaoyu.ticket.agent.domain.plan.PlanNodeStatus;
import com.miaoyu.ticket.agent.domain.plan.PlanNodeType;
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
    void shouldReturnAdminPageWithRepositoryStepStats() {
        CurrentUserAccessor users = mock(CurrentUserAccessor.class);
        UserAdminQueryPort directory = mock(UserAdminQueryPort.class);
        AdminAgentRunQueryRepository runs = mock(AdminAgentRunQueryRepository.class);
        AgentRunStepRepository steps = mock(AgentRunStepRepository.class);
        AgentSessionRepository sessions = mock(AgentSessionRepository.class);
        when(users.requireCurrentUser()).thenReturn(new CurrentUser(9L, RoleCode.ADMIN, 0L));
        when(runs.count(any())).thenReturn(1L);
        when(runs.findPage(any())).thenReturn(List.of(run()));
        when(runs.findNodeStatsByRunIds(List.of(1L))).thenReturn(Map.of(1L,
                new AdminAgentRunQueryRepository.NodeStats(3, 1, 1)));
        when(directory.findByUserIds(any())).thenReturn(Map.of(7L,
                new UserAdminQueryPort.UserAdminSummary(7L, "u***@example.com")));

        var result = service(users, directory, runs, steps, sessions)
                .queryRuns(new AdminAgentRunListQuery("FAILED", null, null, null, 1, 20));

        assertThat(result.total()).isEqualTo(1);
        assertThat(result.records()).singleElement().satisfies(view -> {
            assertThat(view.runId()).isEqualTo("run-1");
            assertThat(view.userDisplay()).isEqualTo("u***@example.com");
            assertThat(view.nodeCount()).isEqualTo(3);
            assertThat(view.completedNodeCount()).isEqualTo(1);
            assertThat(view.failedNodeCount()).isEqualTo(1);
            assertThat(view.errorSummary()).isNull();
        });
    }

    @Test
    void shouldRejectUserBeforeAnyRead() {
        CurrentUserAccessor users = mock(CurrentUserAccessor.class);
        UserAdminQueryPort directory = mock(UserAdminQueryPort.class);
        AdminAgentRunQueryRepository runs = mock(AdminAgentRunQueryRepository.class);
        AgentRunStepRepository steps = mock(AgentRunStepRepository.class);
        AgentSessionRepository sessions = mock(AgentSessionRepository.class);
        when(users.requireCurrentUser()).thenReturn(new CurrentUser(8L, RoleCode.USER, 0L));

        assertThatThrownBy(() -> service(users, directory, runs, steps, sessions)
                .queryRuns(new AdminAgentRunListQuery(null, null, null, null, 1, 20)))
                .isInstanceOf(BusinessException.class)
                .extracting(error -> ((BusinessException) error).getErrorCode().code()).isEqualTo(100403);
        verifyNoInteractions(directory, runs, steps, sessions);
    }

    @Test
    void shouldUseExisting404ForMissingRun() {
        CurrentUserAccessor users = mock(CurrentUserAccessor.class);
        UserAdminQueryPort directory = mock(UserAdminQueryPort.class);
        AdminAgentRunQueryRepository runs = mock(AdminAgentRunQueryRepository.class);
        AgentRunStepRepository steps = mock(AgentRunStepRepository.class);
        AgentSessionRepository sessions = mock(AgentSessionRepository.class);
        when(users.requireCurrentUser()).thenReturn(new CurrentUser(9L, RoleCode.ADMIN, 0L));
        when(runs.findByRunId("missing")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service(users, directory, runs, steps, sessions).queryRun("missing"))
                .isInstanceOfSatisfying(BusinessException.class,
                        error -> assertThat(error.getErrorCode()).isEqualTo(AgentErrorCode.AGENT_RESOURCE_NOT_FOUND));
    }

    @Test
    void shouldReturnNullForUnpersistedToolAndFailureSummaries() {
        CurrentUserAccessor users = mock(CurrentUserAccessor.class);
        UserAdminQueryPort directory = mock(UserAdminQueryPort.class);
        AdminAgentRunQueryRepository runs = mock(AdminAgentRunQueryRepository.class);
        AgentRunStepRepository steps = mock(AgentRunStepRepository.class);
        AgentSessionRepository sessions = mock(AgentSessionRepository.class);
        when(users.requireCurrentUser()).thenReturn(new CurrentUser(9L, RoleCode.ADMIN, 0L));
        when(runs.findByRunId("run-1")).thenReturn(Optional.of(run()));
        when(steps.findByRunId(1L)).thenReturn(List.of(stepWithSensitiveJson()));
        when(sessions.findById(2L)).thenReturn(Optional.empty());
        when(directory.findByUserIds(any())).thenReturn(Map.of(7L,
                new UserAdminQueryPort.UserAdminSummary(7L, "u***@example.com")));

        var result = service(users, directory, runs, steps, sessions).queryRun("run-1");

        assertThat(result.errorCode()).isNull();
        assertThat(result.errorSummary()).isNull();
        assertThat(result.nodes()).singleElement().satisfies(node -> {
            assertThat(node.targetName()).isNull();
            assertThat(node.toolStatus()).isNull();
            assertThat(node.errorCode()).isNull();
            assertThat(node.errorSummary()).isNull();
        });
        assertThat(result.toString()).doesNotContain("tool-secret", "thought-chain", "latitude", "paymentToken");
    }

    private static AdminAgentRunQueryService service(CurrentUserAccessor users, UserAdminQueryPort directory,
            AdminAgentRunQueryRepository runs, AgentRunStepRepository steps, AgentSessionRepository sessions) {
        return new AdminAgentRunQueryService(users, directory, runs, steps, sessions,
                new ApiProperties(1, 20, 100));
    }

    private static AgentRun run() {
        LocalDateTime now = LocalDateTime.of(2026, 8, 5, 19, 20);
        return new AgentRun(1L, "run-1", 2L, 7L, "request-1", new AgentRequestHash("v1",
                "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa"), "plan-1", 1,
                AgentRunStatus.FAILED, "trace", now, now.plusSeconds(8), 0L, now, now, now.plusDays(30));
    }

    private static AgentRunStep stepWithSensitiveJson() {
        LocalDateTime now = LocalDateTime.of(2026, 8, 5, 19, 20);
        return new AgentRunStep(1L, 1L, 1, "node-1", PlanNodeType.CALL_TOOL,
                new AgentStoredJson("{\"thought-chain\":\"tool-secret\"}"),
                new AgentStoredJson("{\"latitude\":39.9,\"paymentToken\":\"tool-secret\"}"),
                PlanNodeStatus.FAILED, FailurePolicy.FAIL, 1, 0, false, false, null, null,
                new AgentStoredJson("{\"toolArgs\":\"tool-secret\"}"), now, now.plusSeconds(1), 0L,
                now, now, now.plusDays(30));
    }
}
