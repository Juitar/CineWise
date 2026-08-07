package com.miaoyu.ticket.agent;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.miaoyu.ticket.agent.infrastructure.persistence.AdminAgentRunStepStatsRow;
import com.miaoyu.ticket.agent.infrastructure.persistence.AgentPersistenceMapper;
import com.miaoyu.ticket.agent.infrastructure.persistence.MybatisAdminAgentRunQueryRepository;
import java.util.List;
import org.junit.jupiter.api.Test;

class MybatisAdminAgentRunQueryRepositoryTest {
    @Test
    void shouldMapCurrentPageStepStatsWithoutReadingStepPayload() {
        AgentPersistenceMapper mapper = mock(AgentPersistenceMapper.class);
        when(mapper.findAdminRunStepStatsByRunIds(List.of(3L, 7L))).thenReturn(List.of(
                new AdminAgentRunStepStatsRow(3L, 2, 1, 0),
                new AdminAgentRunStepStatsRow(7L, 4, 2, 1)));

        var stats = new MybatisAdminAgentRunQueryRepository(mapper).findNodeStatsByRunIds(List.of(3L, 7L));

        assertThat(stats).containsEntry(3L, new com.miaoyu.ticket.agent.application.audit
                .AdminAgentRunQueryRepository.NodeStats(2, 1, 0));
        assertThat(stats).containsEntry(7L, new com.miaoyu.ticket.agent.application.audit
                .AdminAgentRunQueryRepository.NodeStats(4, 2, 1));
    }
}
