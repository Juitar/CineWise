package com.miaoyu.ticket.agent;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.miaoyu.ticket.agent.application.AgentErrorCode;
import com.miaoyu.ticket.agent.application.model.ReplyGenerationResponse;
import com.miaoyu.ticket.agent.application.persistence.AgentInitialRunResult;
import com.miaoyu.ticket.agent.application.persistence.AgentInitialRunTransaction;
import com.miaoyu.ticket.agent.application.persistence.AgentMessageSubmissionCommand;
import com.miaoyu.ticket.agent.application.persistence.AgentMessageSubmissionResult;
import com.miaoyu.ticket.agent.application.persistence.AgentMessageSubmissionService;
import com.miaoyu.ticket.agent.application.persistence.AgentSessionRepository;
import com.miaoyu.ticket.agent.application.reply.AgentReplyMessageType;
import com.miaoyu.ticket.agent.application.reply.ErrorReplyFacts;
import com.miaoyu.ticket.agent.application.run.MinimalReadOnlyAgentResult;
import com.miaoyu.ticket.agent.application.run.MinimalReadOnlyAgentService;
import com.miaoyu.ticket.agent.domain.persistence.AgentRunStatus;
import com.miaoyu.ticket.agent.domain.persistence.AgentSession;
import com.miaoyu.ticket.agent.domain.persistence.AgentSessionStatus;
import com.miaoyu.ticket.agent.domain.plan.CandidatePlan;
import com.miaoyu.ticket.agent.domain.plan.PlanValidationContext;
import com.miaoyu.ticket.agent.domain.plan.PlanValidationIssue;
import com.miaoyu.ticket.agent.domain.plan.PlanValidationIssueCode;
import com.miaoyu.ticket.agent.domain.plan.PlanValidationResult;
import com.miaoyu.ticket.agent.domain.plan.SlotSnapshot;
import com.miaoyu.ticket.auth.application.CurrentUser;
import com.miaoyu.ticket.auth.application.CurrentUserAccessor;
import com.miaoyu.ticket.auth.application.RoleCode;
import com.miaoyu.ticket.common.error.BusinessException;
import java.net.URI;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.ApplicationContextInitializer;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/** 只允许在 CI 的一次性 MySQL 8.4 库中验证 Agent 表约束、并发占用和事务边界。 */
@EnabledIfEnvironmentVariable(named = "CINEWISE_MYSQL_AGENT_PERSISTENCE_IT", matches = "true")
@ActiveProfiles("dev")
@SpringBootTest(properties = {
    "cinewise.seed.enabled=false",
    "spring.flyway.enabled=true",
    "cinewise.transaction.expiry-job-enabled=false",
    "management.health.redis.enabled=false",
    "cinewise.auth.jwt-secret=agent-persistence-it-jwt-secret-at-least-32-bytes",
    "cinewise.auth.audit-hash-secret=agent-persistence-it-audit-secret-at-least-32-bytes"
})
@Import(AgentPersistenceMySqlIntegrationTest.AgentPersistenceTestConfiguration.class)
@ContextConfiguration(initializers = AgentPersistenceMySqlIntegrationTest.CiMySqlSafetyInitializer.class)
class AgentPersistenceMySqlIntegrationTest {
    private static final String REQUIRED_DATABASE = "cinewise_agent_it";
    private static final String REQUIRED_USERNAME = "cinewise_ci";
    private static final long USER_ID = 9_708_000_001L;
    private static final long FIRST_SESSION_ID = 9_708_100_001L;
    private static final long SECOND_SESSION_ID = 9_708_100_002L;
    private static final String FIRST_SESSION = "agent-persistence-it-session-1";
    private static final String SECOND_SESSION = "agent-persistence-it-session-2";

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private AgentSessionRepository sessionRepository;

    @Autowired
    private AgentInitialRunTransaction initialRunTransaction;

    @Autowired
    private AgentMessageSubmissionService messageSubmissionService;

    @Autowired
    private MinimalReadOnlyAgentService minimalReadOnlyAgentService;

    private AtomicBoolean toolCalledInsideTransaction;

    @BeforeEach
    void requireCiDatabaseAndPrepareFixtures() {
        assertThat(jdbcTemplate.queryForObject("SELECT DATABASE()", String.class))
                .as("Agent MySQL 集成测试只能使用 CI 一次性临时库")
                .isEqualTo(REQUIRED_DATABASE);
        assertThat(jdbcTemplate.queryForObject("SELECT VERSION()", String.class)).startsWith("8.4.");
        assertThat(jdbcTemplate.queryForObject("""
                SELECT COUNT(*)
                  FROM flyway_schema_history
                 WHERE version = '008' AND success = 1
                """, Integer.class)).isEqualTo(1);
        cleanupFixtures();
        toolCalledInsideTransaction = new AtomicBoolean(true);
        Mockito.reset(minimalReadOnlyAgentService);
    }

    @AfterEach
    void cleanupMySqlFixtures() {
        cleanupFixtures();
    }

    @Test
    void shouldEnforceSingleActiveRunAndReturnExistingRunForSameRequest() {
        insertSession(FIRST_SESSION_ID, FIRST_SESSION);

        AgentInitialRunResult created = initialRunTransaction.submit(USER_ID, command(FIRST_SESSION, "request-1"));
        AgentInitialRunResult repeated = initialRunTransaction.submit(USER_ID, command(FIRST_SESSION, "request-1"));

        assertThat(repeated.reused()).isTrue();
        assertThat(repeated.run().id()).isEqualTo(created.run().id());
        assertThatThrownBy(() -> initialRunTransaction.submit(USER_ID, command(FIRST_SESSION, "request-2")))
                .isInstanceOf(BusinessException.class)
                .extracting(exception -> ((BusinessException) exception).getErrorCode())
                .isEqualTo(AgentErrorCode.ACTIVE_RUN_CONFLICT);
        assertThat(count("SELECT COUNT(*) FROM agent_run WHERE user_id = ?", USER_ID)).isEqualTo(1);
        assertThat(count("SELECT COUNT(*) FROM agent_message WHERE user_id = ?", USER_ID)).isEqualTo(1);
    }

    @Test
    void shouldAllowOnlyOneConcurrentDifferentRequestToClaimSession() throws Exception {
        insertSession(SECOND_SESSION_ID, SECOND_SESSION);
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            Future<Attempt> first = executor.submit(submitAfterSignal(start, "request-a"));
            Future<Attempt> second = executor.submit(submitAfterSignal(start, "request-b"));
            start.countDown();

            List<Attempt> attempts = List.of(first.get(), second.get());
            assertThat(attempts).filteredOn(Attempt::created).hasSize(1);
            assertThat(attempts).filteredOn(Attempt::activeRunConflict).hasSize(1);
            assertThat(count("SELECT COUNT(*) FROM agent_run WHERE session_id = ?", SECOND_SESSION_ID)).isEqualTo(1);
            assertThat(count("SELECT COUNT(*) FROM agent_message WHERE session_id = ?", SECOND_SESSION_ID))
                    .isEqualTo(1);
        } finally {
            executor.shutdownNow();
        }
    }

    @Test
    void shouldCallReadOnlyAgentOutsideDatabaseTransactionAndPersistFailureResult() {
        insertSession(FIRST_SESSION_ID, FIRST_SESSION);
        Mockito.when(minimalReadOnlyAgentService.run(Mockito.any())).thenAnswer(invocation -> {
            toolCalledInsideTransaction.set(TransactionSynchronizationManager.isActualTransactionActive());
            return invalidResult();
        });

        AgentMessageSubmissionResult result = messageSubmissionService.submit(command(FIRST_SESSION, "request-1"));

        assertThat(toolCalledInsideTransaction).isFalse();
        assertThat(result.snapshot().run().status()).isEqualTo(AgentRunStatus.FAILED);
        assertThat(result.snapshot().messages()).hasSize(2);
        assertThat(sessionRepository.findBySessionIdAndUserId(FIRST_SESSION, USER_ID).orElseThrow().activeRunId())
                .isNull();
    }

    private Callable<Attempt> submitAfterSignal(CountDownLatch start, String requestId) {
        return () -> {
            start.await();
            try {
                return new Attempt(initialRunTransaction.submit(USER_ID, command(SECOND_SESSION, requestId)), null);
            } catch (BusinessException exception) {
                return new Attempt(null, exception);
            }
        };
    }

    private static AgentMessageSubmissionCommand command(String sessionId, String requestId) {
        SlotSnapshot slots = new SlotSnapshot(1L, Map.of());
        return new AgentMessageSubmissionCommand(
                sessionId, "推荐电影", requestId, slots, new PlanValidationContext(Map.of(), Map.of(), slots), 3000L);
    }

    private static MinimalReadOnlyAgentResult invalidResult() {
        return new MinimalReadOnlyAgentResult(
                new CandidatePlan("candidate-1", 1, List.of()),
                PlanValidationResult.invalid(List.of(new PlanValidationIssue(
                        PlanValidationIssueCode.TOOL_NOT_FOUND, "rank", "targetName", "ignored"))),
                null,
                List.of(),
                new ReplyGenerationResponse(
                        "当前请求无法安全执行", AgentReplyMessageType.ERROR,
                        new ErrorReplyFacts(null, List.of("TOOL_NOT_FOUND"))));
    }

    private void insertSession(long id, String sessionId) {
        LocalDateTime now = LocalDateTime.now().withNano(0);
        sessionRepository.insert(new AgentSession(
                id, sessionId, USER_ID, null, AgentSessionStatus.ACTIVE, null, 0L, now, now, now.plusDays(30)));
    }

    private int count(String sql, long parameter) {
        return jdbcTemplate.queryForObject(sql, Integer.class, parameter);
    }

    private void cleanupFixtures() {
        jdbcTemplate.update("""
                DELETE FROM agent_run_step
                 WHERE run_id IN (SELECT id FROM agent_run WHERE user_id = ?)
                """, USER_ID);
        jdbcTemplate.update("DELETE FROM agent_message WHERE user_id = ?", USER_ID);
        jdbcTemplate.update("DELETE FROM agent_run WHERE user_id = ?", USER_ID);
        jdbcTemplate.update("DELETE FROM agent_session WHERE user_id = ?", USER_ID);
    }

    private record Attempt(AgentInitialRunResult result, BusinessException exception) {
        boolean created() {
            return result != null && !result.reused();
        }

        boolean activeRunConflict() {
            return exception != null && exception.getErrorCode() == AgentErrorCode.ACTIVE_RUN_CONFLICT;
        }
    }

    @TestConfiguration(proxyBeanMethods = false)
    static class AgentPersistenceTestConfiguration {
        @Bean
        @Primary
        CurrentUserAccessor agentPersistenceCurrentUserAccessor() {
            CurrentUser currentUser = new CurrentUser(USER_ID, RoleCode.USER, 0L);
            return () -> currentUser;
        }

        @Bean
        @Primary
        MinimalReadOnlyAgentService agentPersistenceMinimalReadOnlyAgentService() {
            return Mockito.mock(MinimalReadOnlyAgentService.class);
        }
    }

    static class CiMySqlSafetyInitializer
            implements ApplicationContextInitializer<ConfigurableApplicationContext> {
        @Override
        public void initialize(ConfigurableApplicationContext context) {
            String dataSourceUrl = context.getEnvironment().getRequiredProperty("spring.datasource.url");
            String username = context.getEnvironment().getRequiredProperty("spring.datasource.username");
            if (!dataSourceUrl.startsWith("jdbc:mysql:") || !REQUIRED_USERNAME.equals(username)) {
                throw new IllegalStateException("Agent MySQL 集成测试必须使用 cinewise_ci 连接 MySQL");
            }
            String database;
            try {
                database = URI.create(dataSourceUrl.substring("jdbc:".length())).getPath().replaceFirst("^/", "");
            } catch (IllegalArgumentException exception) {
                throw new IllegalStateException("Agent MySQL 集成测试的数据源 URL 不合法", exception);
            }
            if (!REQUIRED_DATABASE.equals(database)) {
                throw new IllegalStateException("Agent MySQL 集成测试只能使用 cinewise_agent_it");
            }
        }
    }
}
