package com.miaoyu.ticket.agent;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.miaoyu.ticket.agent.application.AgentErrorCode;
import com.miaoyu.ticket.agent.application.persistence.AgentDistanceRunInitializationService;
import com.miaoyu.ticket.agent.application.persistence.AgentMessageRepository;
import com.miaoyu.ticket.agent.application.persistence.AgentRequestHashFactory;
import com.miaoyu.ticket.agent.application.persistence.AgentRunRepository;
import com.miaoyu.ticket.agent.application.persistence.AgentSessionRepository;
import com.miaoyu.ticket.agent.domain.persistence.AgentRequestHash;
import com.miaoyu.ticket.agent.domain.persistence.AgentRun;
import com.miaoyu.ticket.agent.domain.persistence.AgentRunStatus;
import com.miaoyu.ticket.agent.domain.persistence.AgentSession;
import com.miaoyu.ticket.agent.domain.persistence.AgentSessionStatus;
import com.miaoyu.ticket.auth.application.CurrentUserAccessor;
import com.miaoyu.ticket.common.error.BusinessException;
import com.miaoyu.ticket.common.id.BusinessIdGenerator;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;

/** 初始化距离推荐只能建立等待运行，不能提前执行模型或推荐。 */
class AgentDistanceRunInitializationServiceTest {
    private static final long USER_ID = 7L;
    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-08-07T06:00:00Z"),
            ZoneId.of("Asia/Shanghai"));

    @Test
    void shouldCreateWaitingRunUsingDatabaseWaitingTimestampInsert() {
        Fixture fixture = fixture();
        when(fixture.sessions.findBySessionIdAndUserId("session-1", USER_ID)).thenReturn(Optional.of(session()));
        when(fixture.hashes.create(any(), any())).thenReturn(hash());
        when(fixture.runs.findByClientRequestId(USER_ID, 1L, "request-1")).thenReturn(Optional.empty());
        when(fixture.sessions.claimActiveRun(anyLong(), anyLong(), anyLong(), any())).thenReturn(true);

        AgentRun run = fixture.service.initialize("session-1", "request-1", "推荐电影", "workspace").run();

        assertThat(run.status()).isEqualTo(AgentRunStatus.WAITING_LOCATION);
        assertThat(run.planId()).isNull();
        assertThat(run.planVersion()).isNull();
        assertThat(run.finishedAt()).isNull();
        ArgumentCaptor<AgentRun> inserted = ArgumentCaptor.forClass(AgentRun.class);
        verify(fixture.runs).insertWaitingLocation(inserted.capture());
        assertThat(inserted.getValue().status()).isEqualTo(AgentRunStatus.WAITING_LOCATION);
        verify(fixture.runs, never()).insert(any());
        verify(fixture.messages).insert(any());
    }

    @Test
    void shouldReturnExistingRunWithoutRefreshingWaitingTime() {
        Fixture fixture = fixture();
        AgentRun existing = run(AgentRunStatus.WAITING_LOCATION);
        when(fixture.sessions.findBySessionIdAndUserId("session-1", USER_ID)).thenReturn(Optional.of(session()));
        when(fixture.hashes.create(any(), any())).thenReturn(hash());
        when(fixture.runs.findByClientRequestId(USER_ID, 1L, "request-1")).thenReturn(Optional.of(existing));

        assertThat(fixture.service.initialize("session-1", "request-1", "推荐电影", "workspace").run())
                .isEqualTo(existing);
        verify(fixture.runs, never()).insertWaitingLocation(any());
        verify(fixture.messages, never()).insert(any());
    }

    @Test
    void shouldRejectSameRequestIdWithDifferentContentHash() {
        Fixture fixture = fixture();
        when(fixture.sessions.findBySessionIdAndUserId("session-1", USER_ID)).thenReturn(Optional.of(session()));
        when(fixture.hashes.create(any(), any())).thenReturn(new AgentRequestHash("v1", "b".repeat(64)));
        when(fixture.runs.findByClientRequestId(USER_ID, 1L, "request-1"))
                .thenReturn(Optional.of(run(AgentRunStatus.WAITING_LOCATION)));

        assertThatThrownBy(() -> fixture.service.initialize("session-1", "request-1", "另一条输入", "workspace"))
                .isInstanceOf(BusinessException.class)
                .extracting(error -> ((BusinessException) error).getErrorCode())
                .isEqualTo(AgentErrorCode.REQUEST_HASH_MISMATCH);
    }

    private static Fixture fixture() {
        CurrentUserAccessor users = Mockito.mock(CurrentUserAccessor.class);
        AgentSessionRepository sessions = Mockito.mock(AgentSessionRepository.class);
        AgentRunRepository runs = Mockito.mock(AgentRunRepository.class);
        AgentMessageRepository messages = Mockito.mock(AgentMessageRepository.class);
        AgentRequestHashFactory hashes = Mockito.mock(AgentRequestHashFactory.class);
        when(users.requireCurrentUserId()).thenReturn(USER_ID);
        BusinessIdGenerator ids = new BusinessIdGenerator() {
            private long next = 100L;

            @Override
            public long nextId() {
                return next++;
            }
        };
        return new Fixture(new AgentDistanceRunInitializationService(
                users, sessions, runs, messages, hashes, ids, CLOCK),
                sessions, runs, messages, hashes);
    }

    private static AgentSession session() {
        LocalDateTime now = LocalDateTime.of(2026, 8, 7, 14, 0);
        return new AgentSession(1L, "session-1", USER_ID, null, AgentSessionStatus.ACTIVE, null, 0L,
                now, now, now.plusDays(30));
    }

    private static AgentRun run(AgentRunStatus status) {
        LocalDateTime now = LocalDateTime.of(2026, 8, 7, 14, 0);
        return new AgentRun(100L, "run-1", 1L, USER_ID, "request-1", hash(), null, null, status, "trace-1",
                now, null, 0L, now, now, now.plusDays(30));
    }

    private static AgentRequestHash hash() {
        return new AgentRequestHash("v1", "a".repeat(64));
    }

    private record Fixture(AgentDistanceRunInitializationService service, AgentSessionRepository sessions,
            AgentRunRepository runs, AgentMessageRepository messages, AgentRequestHashFactory hashes) {
    }
}
