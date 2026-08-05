package com.miaoyu.ticket.agent;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.miaoyu.ticket.agent.application.AgentInteractionRuntimeService;
import com.miaoyu.ticket.agent.application.persistence.AgentEventReplayService;
import com.miaoyu.ticket.agent.application.persistence.AgentMessageSubmissionService;
import com.miaoyu.ticket.agent.application.persistence.AgentRuntimeQueryService;
import com.miaoyu.ticket.agent.domain.persistence.AgentEventType;
import com.miaoyu.ticket.agent.domain.persistence.AgentRuntimeEvent;
import com.miaoyu.ticket.agent.domain.persistence.AgentStoredJson;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.Test;

/** Agent 运行时门面在失败连接中只能读取已持久化事件，不能重新提交请求。 */
class AgentInteractionRuntimeServiceTest {

    @Test
    void shouldReplayPersistedEventsWithoutSubmittingAnotherRun() {
        AgentMessageSubmissionService submissionService = mock(AgentMessageSubmissionService.class);
        AgentEventReplayService replayService = mock(AgentEventReplayService.class);
        AgentRuntimeQueryService queryService = mock(AgentRuntimeQueryService.class);
        AgentInteractionRuntimeService service = new AgentInteractionRuntimeService(submissionService, replayService,
                queryService, new ObjectMapper());
        LocalDateTime time = LocalDateTime.of(2026, 8, 5, 11, 20);
        AgentRuntimeEvent error = new AgentRuntimeEvent(8L, "session-1", "run-1", AgentEventType.MESSAGE_ERROR,
                new AgentStoredJson("{\"reason\":\"RUN_FAILED\"}"), time.plusDays(30), time);
        AgentRuntimeEvent complete = new AgentRuntimeEvent(9L, "session-1", "run-1", AgentEventType.RUN_COMPLETE,
                new AgentStoredJson("{\"status\":\"FAILED\"}"), time.plusDays(30), time);
        when(replayService.replay("session-1", 0L))
                .thenReturn(new AgentEventReplayService.ReplayResult(List.of(error, complete), false, 9L));

        var replay = service.replayPersistedEvents("session-1", 0L);

        assertThat(replay.runId()).isEqualTo("run-1");
        assertThat(replay.events()).extracting(AgentInteractionRuntimeService.EventView::eventType)
                .containsExactly("message.error", "run.complete");
        verify(replayService).replay("session-1", 0L);
        verifyNoInteractions(submissionService, queryService);
    }
}
