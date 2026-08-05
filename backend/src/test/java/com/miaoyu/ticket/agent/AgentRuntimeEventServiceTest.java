package com.miaoyu.ticket.agent;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verifyNoInteractions;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.miaoyu.ticket.agent.application.persistence.AgentRuntimeEventRepository;
import com.miaoyu.ticket.agent.application.persistence.AgentRuntimeEventService;
import com.miaoyu.ticket.agent.application.persistence.AgentSessionRepository;
import com.miaoyu.ticket.agent.domain.persistence.AgentEventType;
import com.miaoyu.ticket.agent.domain.persistence.AgentStoredJson;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

/** 载荷不合法时必须在原事实事务写入前失败。 */
class AgentRuntimeEventServiceTest {

    @Test
    void shouldRejectNonObjectAndOversizedPayloadBeforeRepositoryAccess() {
        AgentSessionRepository sessionRepository = Mockito.mock(AgentSessionRepository.class);
        AgentRuntimeEventRepository eventRepository = Mockito.mock(AgentRuntimeEventRepository.class);
        AgentRuntimeEventService service = new AgentRuntimeEventService(
                sessionRepository, eventRepository, new ObjectMapper());

        assertThatThrownBy(() -> service.append(null, null, AgentEventType.MESSAGE_START, new AgentStoredJson("[]")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("JSON 对象");
        String oversized = "{\"text\":\"" + "x".repeat(16 * 1024) + "\"}";
        assertThatThrownBy(() -> service.append(null, null, AgentEventType.MESSAGE_START,
                new AgentStoredJson(oversized)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("16 KiB");
        assertThatThrownBy(() -> service.append(null, null, AgentEventType.MESSAGE_START,
                new AgentStoredJson("{\"token\":\"secret\"}")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("禁止字段");

        verifyNoInteractions(sessionRepository, eventRepository);
    }
}
