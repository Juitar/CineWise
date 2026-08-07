package com.miaoyu.ticket.agent;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.miaoyu.ticket.agent.application.AgentErrorCode;
import com.miaoyu.ticket.agent.application.persistence.AgentLocationWaitingService;
import com.miaoyu.ticket.agent.application.persistence.AgentRunRepository;
import com.miaoyu.ticket.agent.domain.persistence.AgentRequestHash;
import com.miaoyu.ticket.agent.domain.persistence.AgentRun;
import com.miaoyu.ticket.agent.domain.persistence.AgentRunStatus;
import com.miaoyu.ticket.auth.application.CurrentUserAccessor;
import com.miaoyu.ticket.common.error.BusinessException;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;

/** 等待定位只允许当前用户的已保存运行以 RUNNING CAS 进入非终态。 */
class AgentLocationWaitingServiceTest {
    private static final long USER_ID = 7L;
    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-08-07T06:00:00Z"), ZoneId.of("Asia/Shanghai"));

    @Test
    void shouldEnterWaitingWithRunningCasAndNoFinishedTime() {
        AgentRunRepository repository = Mockito.mock(AgentRunRepository.class);
        AgentRun running = run(AgentRunStatus.RUNNING);
        when(repository.findByRunIdAndUserId("run-1", USER_ID)).thenReturn(Optional.of(running));
        when(repository.updateWithCas(any(), eq(0L), eq(AgentRunStatus.RUNNING))).thenReturn(true);

        AgentRun result = service(repository).enterWaiting("run-1");

        assertThat(result.status()).isEqualTo(AgentRunStatus.WAITING_LOCATION);
        assertThat(result.finishedAt()).isNull();
        ArgumentCaptor<AgentRun> captured = ArgumentCaptor.forClass(AgentRun.class);
        verify(repository).updateWithCas(captured.capture(), eq(0L), eq(AgentRunStatus.RUNNING));
        assertThat(captured.getValue().updateTime()).isEqualTo(LocalDateTime.of(2026, 8, 7, 14, 0));
    }

    @Test
    void shouldNotRewriteExistingWaitingRun() {
        AgentRunRepository repository = Mockito.mock(AgentRunRepository.class);
        AgentRun waiting = run(AgentRunStatus.WAITING_LOCATION);
        when(repository.findByRunIdAndUserId("run-1", USER_ID)).thenReturn(Optional.of(waiting));

        assertThat(service(repository).enterWaiting("run-1")).isEqualTo(waiting);
        verify(repository, Mockito.never()).updateWithCas(any(), Mockito.anyLong(), any());
    }

    @Test
    void shouldRejectTerminalRun() {
        AgentRunRepository repository = Mockito.mock(AgentRunRepository.class);
        when(repository.findByRunIdAndUserId("run-1", USER_ID)).thenReturn(Optional.of(run(AgentRunStatus.COMPLETED)));

        assertThatThrownBy(() -> service(repository).enterWaiting("run-1"))
                .isInstanceOf(BusinessException.class)
                .extracting(error -> ((BusinessException) error).getErrorCode())
                .isEqualTo(AgentErrorCode.ACTIVE_RUN_CONFLICT);
    }

    private static AgentLocationWaitingService service(AgentRunRepository repository) {
        CurrentUserAccessor currentUser = Mockito.mock(CurrentUserAccessor.class);
        when(currentUser.requireCurrentUserId()).thenReturn(USER_ID);
        return new AgentLocationWaitingService(currentUser, repository, CLOCK);
    }

    private static AgentRun run(AgentRunStatus status) {
        LocalDateTime now = LocalDateTime.of(2026, 8, 7, 12, 0);
        return new AgentRun(2L, "run-1", 1L, USER_ID, "request-1", new AgentRequestHash("v1", "a".repeat(64)),
                "plan-1", 1, status, "trace-1", now, status.isTerminal() ? now : null, 0L, now, now,
                now.plusDays(30));
    }
}
