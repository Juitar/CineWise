package com.miaoyu.ticket.agent;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.miaoyu.ticket.agent.application.AgentErrorCode;
import com.miaoyu.ticket.agent.application.confirmation.AgentConfirmationActionRepository;
import com.miaoyu.ticket.agent.application.confirmation.AgentConfirmationFactsProvider;
import com.miaoyu.ticket.agent.application.confirmation.AgentConfirmationService;
import com.miaoyu.ticket.agent.application.confirmation.CreateOrderToolAdapter;
import com.miaoyu.ticket.agent.application.confirmation.CreateOrderToolResult;
import com.miaoyu.ticket.agent.application.model.ReplyGenerationResponse;
import com.miaoyu.ticket.agent.application.persistence.AgentInitialRunResult;
import com.miaoyu.ticket.agent.application.persistence.AgentInitialRunTransaction;
import com.miaoyu.ticket.agent.application.persistence.AgentMessageSubmissionCommand;
import com.miaoyu.ticket.agent.application.persistence.AgentMessageSubmissionResult;
import com.miaoyu.ticket.agent.application.persistence.AgentMessageSubmissionService;
import com.miaoyu.ticket.agent.application.persistence.AgentRuntimeEventService;
import com.miaoyu.ticket.agent.application.persistence.AgentRunCancellationService;
import com.miaoyu.ticket.agent.application.persistence.AgentSessionManagementService;
import com.miaoyu.ticket.agent.application.persistence.AgentSessionRepository;
import com.miaoyu.ticket.agent.application.reply.AgentReplyMessageType;
import com.miaoyu.ticket.agent.application.reply.ErrorReplyFacts;
import com.miaoyu.ticket.agent.application.run.MinimalReadOnlyAgentResult;
import com.miaoyu.ticket.agent.application.run.MinimalReadOnlyAgentService;
import com.miaoyu.ticket.agent.domain.persistence.AgentRunStatus;
import com.miaoyu.ticket.agent.domain.persistence.AgentEventType;
import com.miaoyu.ticket.agent.domain.persistence.AgentRuntimeEvent;
import com.miaoyu.ticket.agent.domain.persistence.AgentSession;
import com.miaoyu.ticket.agent.domain.persistence.AgentSessionStatus;
import com.miaoyu.ticket.agent.domain.persistence.AgentStoredJson;
import com.miaoyu.ticket.agent.domain.plan.CandidatePlan;
import com.miaoyu.ticket.agent.domain.plan.PlanValidationContext;
import com.miaoyu.ticket.agent.domain.plan.PlanValidationIssue;
import com.miaoyu.ticket.agent.domain.plan.PlanValidationIssueCode;
import com.miaoyu.ticket.agent.domain.plan.PlanValidationResult;
import com.miaoyu.ticket.agent.domain.plan.SlotSnapshot;
import com.miaoyu.ticket.agent.domain.plan.PlanNodeStatus;
import com.miaoyu.ticket.agent.domain.confirmation.AgentConfirmationAction;
import com.miaoyu.ticket.agent.domain.confirmation.AgentConfirmationActionStatus;
import com.miaoyu.ticket.agent.domain.confirmation.AgentActionWriteIdentifiers;
import com.miaoyu.ticket.agent.domain.confirmation.ConfirmedOrderCommand;
import com.miaoyu.ticket.agent.domain.confirmation.AgentConfirmationValidationContext;
import com.miaoyu.ticket.agent.domain.tool.ToolResult;
import com.miaoyu.ticket.agent.domain.tool.ToolStatus;
import com.miaoyu.ticket.agent.domain.tool.ToolContext;
import com.miaoyu.ticket.auth.application.CurrentUser;
import com.miaoyu.ticket.auth.application.CurrentUserAccessor;
import com.miaoyu.ticket.auth.application.RoleCode;
import com.miaoyu.ticket.common.error.BusinessException;
import com.miaoyu.ticket.order.api.CreateOrderForAgentCommand;
import com.miaoyu.ticket.order.api.CreateOrderTool;
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
import java.util.concurrent.atomic.AtomicInteger;
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
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.transaction.PlatformTransactionManager;

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
    private AgentRuntimeEventService runtimeEventService;

    @Autowired
    private AgentRunCancellationService runCancellationService;

    @Autowired
    private AgentSessionManagementService sessionManagementService;

    @Autowired
    private PlatformTransactionManager transactionManager;

    @Autowired
    private AgentConfirmationActionRepository actionRepository;

    @Autowired
    private AgentConfirmationService confirmationService;

    @Autowired
    private CreateOrderToolAdapter createOrderToolAdapter;

    @Autowired
    private CreateOrderTool createOrderTool;

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
        assertThat(jdbcTemplate.queryForObject("""
                SELECT COUNT(*)
                  FROM flyway_schema_history
                 WHERE version = '009' AND success = 1
                """, Integer.class)).isEqualTo(1);
        assertThat(jdbcTemplate.queryForObject("""
                SELECT COUNT(*)
                  FROM flyway_schema_history
                 WHERE version = '012' AND success = 1
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
        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM agent_event WHERE session_id = ?", Integer.class, FIRST_SESSION)).isEqualTo(1);
        assertThat(jdbcTemplate.queryForObject("""
                SELECT last_committed_event_id
                  FROM agent_event_stream_cursor
                 WHERE session_id = ?
                """, Long.class, FIRST_SESSION)).isGreaterThan(0L);
    }

    @Test
    void shouldRejectSubmissionAndClaimAfterSessionIsCleared() {
        insertSession(FIRST_SESSION_ID, FIRST_SESSION);
        assertThat(sessionManagementService.clearMySession(FIRST_SESSION).cleared()).isTrue();

        assertThatThrownBy(() -> initialRunTransaction.submit(USER_ID, command(FIRST_SESSION, "cleared-request")))
                .isInstanceOf(BusinessException.class)
                .extracting(exception -> ((BusinessException) exception).getErrorCode())
                .isEqualTo(AgentErrorCode.AGENT_RESOURCE_NOT_FOUND);
        assertThat(sessionRepository.claimActiveRun(FIRST_SESSION_ID, USER_ID, 9_708_100_901L,
                LocalDateTime.now().plusDays(30))).isFalse();
        assertThat(count("SELECT COUNT(*) FROM agent_run WHERE session_id = ?", FIRST_SESSION_ID)).isZero();
        assertThat(count("SELECT COUNT(*) FROM agent_message WHERE session_id = ?", FIRST_SESSION_ID)).isZero();
        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM agent_event WHERE session_id = ?", Integer.class, FIRST_SESSION)).isZero();
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
    void shouldReturnWinnerForConcurrentSameClientRequestId() throws Exception {
        insertSession(FIRST_SESSION_ID, FIRST_SESSION);
        Mockito.when(minimalReadOnlyAgentService.run(Mockito.any())).thenReturn(invalidResult());
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            Future<AgentMessageSubmissionResult> first = executor.submit(() -> {
                start.await();
                return messageSubmissionService.submit(command(FIRST_SESSION, "same-request"));
            });
            Future<AgentMessageSubmissionResult> second = executor.submit(() -> {
                start.await();
                return messageSubmissionService.submit(command(FIRST_SESSION, "same-request"));
            });
            start.countDown();

            List<AgentMessageSubmissionResult> results = List.of(first.get(), second.get());
            assertThat(results.getFirst().snapshot().run().id()).isEqualTo(results.get(1).snapshot().run().id());
            assertThat(results).filteredOn(AgentMessageSubmissionResult::reused).hasSize(1);
            assertThat(count("SELECT COUNT(*) FROM agent_run WHERE session_id = ?", FIRST_SESSION_ID)).isEqualTo(1);
            assertThat(count("SELECT COUNT(*) FROM agent_message WHERE session_id = ?", FIRST_SESSION_ID)).isEqualTo(2);
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

    @Test
    void shouldWaitForSameSessionLockBeforeAllocatingSecondEventId() throws Exception {
        insertSession(FIRST_SESSION_ID, FIRST_SESSION);
        AgentInitialRunResult initial = initialRunTransaction.submit(USER_ID, command(FIRST_SESSION, "request-lock"));
        AgentSession sourceSession = sessionRepository.findBySessionIdAndUserId(FIRST_SESSION, USER_ID).orElseThrow();
        CountDownLatch firstLockHeld = new CountDownLatch(1);
        CountDownLatch allowFirstCommit = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            Future<AgentRuntimeEvent> first = executor.submit(() -> new TransactionTemplate(transactionManager).execute(
                    status -> {
                        AgentSession locked = sessionRepository
                                .findBySessionIdAndUserIdForUpdate(FIRST_SESSION, USER_ID).orElseThrow();
                        firstLockHeld.countDown();
                        try {
                            allowFirstCommit.await();
                        } catch (InterruptedException exception) {
                            Thread.currentThread().interrupt();
                            throw new IllegalStateException("等待会话锁测试被中断", exception);
                        }
                        return runtimeEventService.append(locked, initial.run(), AgentEventType.PLAN_CREATED,
                                new AgentStoredJson("{\"planVersion\":1}"));
                    }));
            assertThat(firstLockHeld.await(2, java.util.concurrent.TimeUnit.SECONDS)).isTrue();
            Future<AgentRuntimeEvent> second = executor.submit(() -> runtimeEventService.append(sourceSession,
                    initial.run(), AgentEventType.MESSAGE_COMPLETE, new AgentStoredJson("{\"messageType\":\"TEXT\"}")));

            Thread.sleep(150L);
            assertThat(second.isDone()).as("第二个事务必须在会话行锁释放前等待").isFalse();
            allowFirstCommit.countDown();

            AgentRuntimeEvent firstEvent = first.get();
            AgentRuntimeEvent secondEvent = second.get();
            assertThat(firstEvent.eventId()).isLessThan(secondEvent.eventId());
            assertThat(jdbcTemplate.queryForObject("""
                    SELECT last_committed_event_id
                      FROM agent_event_stream_cursor
                     WHERE session_id = ?
                    """, Long.class, FIRST_SESSION)).isEqualTo(secondEvent.eventId());
        } finally {
            allowFirstCommit.countDown();
            executor.shutdownNow();
        }
    }

    @Test
    void shouldLeaveNoActiveRunReferenceWhenCancelAndClearRace() throws Exception {
        insertSession(FIRST_SESSION_ID, FIRST_SESSION);
        AgentInitialRunResult initial = initialRunTransaction.submit(USER_ID, command(FIRST_SESSION, "request-cancel"));
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            Future<AgentRunStatus> cancelled = executor.submit(() -> {
                start.await();
                return runCancellationService.cancelMyRun(initial.run().runId()).status();
            });
            Future<Boolean> cleared = executor.submit(() -> {
                start.await();
                try {
                    return sessionManagementService.clearMySession(FIRST_SESSION).cleared();
                } catch (BusinessException exception) {
                    assertThat(exception.getErrorCode()).isEqualTo(AgentErrorCode.ACTIVE_RUN_CONFLICT);
                    return false;
                }
            });
            start.countDown();

            assertThat(cancelled.get()).isEqualTo(AgentRunStatus.CANCELLED);
            cleared.get();
            AgentSession session = sessionRepository.findBySessionIdAndUserId(FIRST_SESSION, USER_ID).orElseThrow();
            assertThat(session.activeRunId()).isNull();
            assertThat(session.status()).isIn(AgentSessionStatus.ACTIVE, AgentSessionStatus.CLEARED);
        } finally {
            start.countDown();
            executor.shutdownNow();
        }
    }

    @Test
    void shouldPersistActionAndAllowOnlyOneCasClaim() {
        AgentConfirmationAction pending = action("action-cas");
        actionRepository.insert(pending);
        assertThat(actionRepository.findByCreationKey(
                pending.userId(), pending.agentRunId(), pending.planId(), pending.planVersion(), pending.nodeId(),
                pending.command().toolName(), pending.parameterHash().value())).contains(pending);

        AgentConfirmationAction claimed = pending.claim(
                AgentActionWriteIdentifiers.forAction(pending.actionId()), LocalDateTime.now().withNano(0));
        assertThat(actionRepository.compareAndSet(
                pending.actionId(), pending.version(), pending.status(), claimed)).isTrue();
        assertThat(actionRepository.compareAndSet(
                pending.actionId(), pending.version(), pending.status(), claimed)).isFalse();

        AgentConfirmationAction stored = actionRepository.findByActionId(pending.actionId()).orElseThrow();
        assertThat(stored.status()).isEqualTo(AgentConfirmationActionStatus.EXECUTING);
        assertThat(stored.writeIdentifiers().clientRequestId()).isEqualTo(claimed.writeIdentifiers().clientRequestId());
        assertThat(stored.command().sortedSeatIds()).containsExactly("2", "4");
    }

    @Test
    void shouldEnterResultUnknownWithOriginalKeysAndRollbackDoesNotLeaveAction() {
        AgentConfirmationAction pending = action("action-unknown");
        actionRepository.insert(pending);
        AgentConfirmationAction claimed = pending.claim(
                AgentActionWriteIdentifiers.forAction(pending.actionId()), LocalDateTime.now().withNano(0));
        assertThat(actionRepository.compareAndSet(
                pending.actionId(), pending.version(), pending.status(), claimed)).isTrue();
        AgentConfirmationAction unknown = claimed.markResultUnknown("结果确认中", LocalDateTime.now().withNano(0));
        assertThat(actionRepository.compareAndSet(
                claimed.actionId(), claimed.version(), claimed.status(), unknown)).isTrue();
        AgentConfirmationAction stored = actionRepository.findByActionId(pending.actionId()).orElseThrow();
        assertThat(stored.status()).isEqualTo(AgentConfirmationActionStatus.RESULT_UNKNOWN);
        assertThat(stored.recoveryUntil()).isEqualTo(stored.resultUnknownAt().plusDays(30));
        assertThat(stored.writeIdentifiers()).isEqualTo(claimed.writeIdentifiers());

        assertThatThrownBy(() -> new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
            // 创建去重键包含 nodeId；使用独立节点确保真的进入插入和事务回滚分支。
            actionRepository.insert(action("action-rollback", "confirm-order-rollback"));
            throw new IllegalStateException("test rollback");
        })).isInstanceOf(IllegalStateException.class);
        assertThat(actionRepository.findByActionId("action-rollback")).isEmpty();
    }

    @Test
    void shouldInvokeCreateOrderOnlyOnceForConcurrentActionConfirmation() throws Exception {
        AgentConfirmationAction pending = action("action-concurrent");
        actionRepository.insert(pending);
        AtomicInteger executions = new AtomicInteger();
        Mockito.when(createOrderToolAdapter.execute(Mockito.eq("action-concurrent"), Mockito.any(), Mockito.any()))
                .thenAnswer(invocation -> {
                    toolCalledInsideTransaction.set(TransactionSynchronizationManager.isActualTransactionActive());
                    executions.incrementAndGet();
                    return new ToolResult<>(ToolStatus.SUCCESS, new CreateOrderToolResult("order-ref"), null,
                            false, false, null, false, null, null, null, null);
                });
        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            CountDownLatch start = new CountDownLatch(1);
            Future<?> first = executor.submit(() -> {
                start.await();
                return confirmationService.confirm("action-concurrent", true, "trace-concurrent-1");
            });
            Future<?> second = executor.submit(() -> {
                start.await();
                return confirmationService.confirm("action-concurrent", true, "trace-concurrent-2");
            });
            start.countDown();
            first.get();
            second.get();

            assertThat(executions).hasValue(1);
            assertThat(toolCalledInsideTransaction).isFalse();
            assertThat(actionRepository.findByActionId("action-concurrent").orElseThrow().status())
                    .isEqualTo(AgentConfirmationActionStatus.SUCCEEDED);
        } finally {
            executor.shutdownNow();
        }
    }

    @Test
    void shouldRejectPendingActionBeforeCreateOrderToolWritesOrderOrLocksSeat() {
        AgentConfirmationAction pending = action("123e4567-e89b-42d3-a456-426614174001");
        actionRepository.insert(pending);
        int ordersBefore = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM ticket_order", Integer.class);
        int orderSeatsBefore = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM ticket_order_seat", Integer.class);
        int lockedSeatsBefore = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM show_seat WHERE status = 'LOCKED'", Integer.class);
        ToolContext context = new ToolContext(
                pending.runId(),
                pending.nodeId(),
                pending.command().toolName(),
                List.of(),
                3_000L,
                "trace-authorization-rejected",
                "authorization-rejected-request",
                "authorization-rejected-key",
                pending.version());

        ToolResult<?> result = createOrderTool.execute(
                context,
                new CreateOrderForAgentCommand(
                        pending.actionId(),
                        pending.command().showId(),
                        pending.command().sortedSeatIds()));

        assertThat(result.status()).isEqualTo(ToolStatus.FAILED);
        assertThat(result.errorCode()).isEqualTo(205004);
        assertThat(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM ticket_order", Integer.class))
                .isEqualTo(ordersBefore);
        assertThat(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM ticket_order_seat", Integer.class))
                .isEqualTo(orderSeatsBefore);
        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM show_seat WHERE status = 'LOCKED'", Integer.class))
                .isEqualTo(lockedSeatsBefore);
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
        jdbcTemplate.update("DELETE FROM agent_action WHERE user_id = ?", USER_ID);
        jdbcTemplate.update("DELETE FROM agent_event WHERE session_id IN (?, ?)", FIRST_SESSION, SECOND_SESSION);
        jdbcTemplate.update("DELETE FROM agent_event_stream_cursor WHERE session_id IN (?, ?)", FIRST_SESSION,
                SECOND_SESSION);
        jdbcTemplate.update("""
                DELETE FROM agent_run_step
                 WHERE run_id IN (SELECT id FROM agent_run WHERE user_id = ?)
                """, USER_ID);
        jdbcTemplate.update("DELETE FROM agent_message WHERE user_id = ?", USER_ID);
        jdbcTemplate.update("DELETE FROM agent_run WHERE user_id = ?", USER_ID);
        jdbcTemplate.update("DELETE FROM agent_session WHERE user_id = ?", USER_ID);
    }

    private static AgentConfirmationAction action(String actionId) {
        return action(actionId, "confirm-order");
    }

    private static AgentConfirmationAction action(String actionId, String nodeId) {
        LocalDateTime now = LocalDateTime.now().withNano(0);
        return AgentConfirmationAction.pending(9_708_300_001L + Math.abs(actionId.hashCode()), actionId, USER_ID,
                FIRST_SESSION_ID, 9_708_200_001L, "agent-action-run", "agent-action-plan", 1, nodeId,
                new ConfirmedOrderCommand("createOrder", "70001", List.of("2", "4")), now.plusMinutes(5), now);
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

        @Bean
        @Primary
        AgentConfirmationFactsProvider agentConfirmationFactsProvider() {
            return (action, userId) -> new AgentConfirmationValidationContext(
                    userId, AgentRunStatus.RUNNING, action.planId(), action.planVersion(),
                    PlanNodeStatus.WAITING_CONFIRMATION, action.parameterHash(), true, LocalDateTime.now());
        }

        @Bean
        @Primary
        CreateOrderToolAdapter agentConfirmationCreateOrderToolAdapter() {
            return Mockito.mock(CreateOrderToolAdapter.class);
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
