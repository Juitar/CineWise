package com.miaoyu.ticket.agent.infrastructure.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import com.miaoyu.ticket.agent.domain.persistence.AgentEventType;
import com.miaoyu.ticket.agent.domain.persistence.AgentRuntimeEventDraft;
import com.miaoyu.ticket.agent.domain.persistence.AgentStoredJson;
import java.time.LocalDateTime;
import org.junit.jupiter.api.Test;

class AgentPersistenceMappingsTest {

    private static final LocalDateTime NOW = LocalDateTime.of(2026, 8, 7, 10, 0);

    @Test
    void shouldPersistToolCompleteAsLegacyResultAndRestorePublicType() {
        AgentRuntimeEventEntity entity = AgentPersistenceMappings.toEntity(1L, draft(
                AgentEventType.TOOL_COMPLETE, "{\"nodeId\":\"rank\",\"degraded\":false}"));

        assertThat(entity.eventType()).isEqualTo("tool.result");
        assertThat(AgentPersistenceMappings.toDomain(entity).type()).isEqualTo(AgentEventType.TOOL_COMPLETE);
    }

    @Test
    void shouldPersistToolErrorAsLegacyResultAndRestorePublicType() {
        AgentRuntimeEventEntity entity = AgentPersistenceMappings.toEntity(2L, draft(
                AgentEventType.TOOL_ERROR, "{\"nodeId\":\"rank\",\"errorCode\":\"TOOL_FAILED\"}"));

        assertThat(entity.eventType()).isEqualTo("tool.result");
        assertThat(AgentPersistenceMappings.toDomain(entity).type()).isEqualTo(AgentEventType.TOOL_ERROR);
    }

    @Test
    void shouldKeepHistoricalToolResultWithoutTerminalMarker() {
        AgentRuntimeEventEntity entity = new AgentRuntimeEventEntity(
                3L, "session", "run", "tool.result", "{\"nodeId\":\"rank\"}", NOW.plusDays(1), NOW);

        assertThat(AgentPersistenceMappings.toDomain(entity).type()).isEqualTo(AgentEventType.TOOL_RESULT);
    }

    private static AgentRuntimeEventDraft draft(AgentEventType type, String payload) {
        return new AgentRuntimeEventDraft(
                "session", "run", type, new AgentStoredJson(payload), NOW.plusDays(1), NOW);
    }
}
