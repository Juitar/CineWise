package com.miaoyu.ticket.agent;

import static org.assertj.core.api.Assertions.assertThat;

import com.miaoyu.ticket.agent.application.persistence.AgentMessageRepository;
import com.miaoyu.ticket.agent.application.persistence.AgentSessionManagementService;
import com.miaoyu.ticket.agent.application.persistence.AgentSessionRepository;
import com.miaoyu.ticket.agent.domain.persistence.AgentMessage;
import com.miaoyu.ticket.agent.domain.persistence.AgentMessageRole;
import com.miaoyu.ticket.agent.domain.persistence.AgentMessageStatus;
import com.miaoyu.ticket.agent.domain.persistence.AgentMessageType;
import com.miaoyu.ticket.agent.domain.persistence.AgentSession;
import com.miaoyu.ticket.agent.domain.persistence.AgentSessionStatus;
import com.miaoyu.ticket.agent.domain.persistence.AgentStoredJson;
import com.miaoyu.ticket.auth.application.CurrentUser;
import com.miaoyu.ticket.auth.application.CurrentUserAccessor;
import com.miaoyu.ticket.auth.application.RoleCode;
import java.time.LocalDateTime;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

/** H2 兼容验证本次新增 Mapper SQL；真实 MySQL 并发场景由条件集成测试另行执行。 */
@ActiveProfiles("test")
@SpringBootTest
@Import(AgentSessionPersistenceIntegrationTest.TestCurrentUserConfiguration.class)
class AgentSessionPersistenceIntegrationTest {
    private static final long USER_ID = 9_709_900_001L;

    @Autowired
    private AgentSessionRepository sessionRepository;

    @Autowired
    private AgentMessageRepository messageRepository;

    @Autowired
    private AgentSessionManagementService sessionManagementService;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @AfterEach
    void cleanup() {
        jdbcTemplate.update("DELETE FROM agent_event WHERE session_id LIKE 'session-control-%'");
        jdbcTemplate.update("DELETE FROM agent_event_stream_cursor WHERE session_id LIKE 'session-control-%'");
        jdbcTemplate.update("DELETE FROM agent_message WHERE user_id = ?", USER_ID);
        jdbcTemplate.update(
                "DELETE FROM agent_run_step WHERE run_id IN (SELECT id FROM agent_run WHERE user_id = ?)", USER_ID);
        jdbcTemplate.update("DELETE FROM agent_run WHERE user_id = ?", USER_ID);
        jdbcTemplate.update("DELETE FROM agent_session WHERE user_id = ?", USER_ID);
    }

    @Test
    void shouldPageMessagesAndClearOnlyInactiveSessionThroughRealMapper() {
        LocalDateTime now = LocalDateTime.of(2026, 8, 5, 12, 0);
        AgentSession idle = session(9_709_900_011L, "session-control-idle", null, now);
        AgentSession running = session(9_709_900_012L, "session-control-running", 9_709_900_101L, now.plusMinutes(1));
        sessionRepository.insert(idle);
        sessionRepository.insert(running);
        messageRepository.insert(new AgentMessage(9_709_900_021L, "message-control-1", idle.id(), 9_709_900_201L,
                USER_ID, AgentMessageRole.ASSISTANT, AgentMessageType.TEXT, "历史消息", new AgentStoredJson("{}"),
                AgentMessageStatus.COMPLETED, now, now, now.plusDays(30)));

        var sessions = sessionManagementService.listMySessions(1, 20);
        var messages = sessionManagementService.listMySessionMessages(idle.sessionId(), 1, 20);
        var bulk = sessionManagementService.clearMySessions();

        assertThat(sessions.total()).isEqualTo(2L);
        assertThat(messages.records()).extracting(AgentMessage::messageId).containsExactly("message-control-1");
        assertThat(bulk.clearedCount()).isEqualTo(1);
        assertThat(bulk.skippedCount()).isEqualTo(1);
        assertThat(sessionRepository.findBySessionIdAndUserId(idle.sessionId(), USER_ID).orElseThrow().status())
                .isEqualTo(AgentSessionStatus.CLEARED);
        assertThat(sessionRepository.findBySessionIdAndUserId(running.sessionId(), USER_ID).orElseThrow().status())
                .isEqualTo(AgentSessionStatus.ACTIVE);
    }

    private static AgentSession session(long id, String sessionId, Long activeRunId, LocalDateTime now) {
        return new AgentSession(id, sessionId, USER_ID, null, AgentSessionStatus.ACTIVE, activeRunId, 0L,
                now, now, now.plusDays(30));
    }

    @TestConfiguration(proxyBeanMethods = false)
    static class TestCurrentUserConfiguration {
        @Bean
        @Primary
        CurrentUserAccessor agentSessionControlCurrentUserAccessor() {
            return () -> new CurrentUser(USER_ID, RoleCode.USER, 0L);
        }
    }
}
