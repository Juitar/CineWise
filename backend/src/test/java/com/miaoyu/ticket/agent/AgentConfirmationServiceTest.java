package com.miaoyu.ticket.agent;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.miaoyu.ticket.agent.application.confirmation.AgentConfirmationActionRepository;
import com.miaoyu.ticket.agent.application.confirmation.AgentActionAuthorizationDeniedException;
import com.miaoyu.ticket.agent.application.confirmation.AgentActionAuthorizationFacts;
import com.miaoyu.ticket.agent.application.confirmation.AgentActionAuthorizationRequest;
import com.miaoyu.ticket.agent.application.confirmation.AgentActionAuthorizationService;
import com.miaoyu.ticket.agent.application.confirmation.AgentConfirmationFactsProvider;
import com.miaoyu.ticket.agent.application.confirmation.AgentConfirmationResult;
import com.miaoyu.ticket.agent.application.confirmation.AgentConfirmationService;
import com.miaoyu.ticket.agent.application.confirmation.CreateOrderToolAdapter;
import com.miaoyu.ticket.agent.application.confirmation.CreateOrderToolResult;
import com.miaoyu.ticket.agent.api.AgentActionResponse;
import com.miaoyu.ticket.agent.domain.confirmation.AgentConfirmationAction;
import com.miaoyu.ticket.agent.domain.confirmation.AgentActionWriteIdentifiers;
import com.miaoyu.ticket.agent.domain.confirmation.AgentConfirmationCardStatus;
import com.miaoyu.ticket.agent.domain.confirmation.AgentConfirmationActionStatus;
import com.miaoyu.ticket.agent.domain.confirmation.AgentConfirmationValidationContext;
import com.miaoyu.ticket.agent.domain.confirmation.AgentConfirmationValidationFailure;
import com.miaoyu.ticket.agent.domain.confirmation.ConfirmedOrderCommand;
import com.miaoyu.ticket.agent.domain.persistence.AgentRunStatus;
import com.miaoyu.ticket.agent.domain.plan.PlanNodeStatus;
import com.miaoyu.ticket.agent.domain.tool.ToolContext;
import com.miaoyu.ticket.agent.domain.tool.ToolResult;
import com.miaoyu.ticket.agent.domain.tool.ToolStatus;
import com.miaoyu.ticket.auth.application.CurrentUser;
import com.miaoyu.ticket.auth.application.CurrentUserAccessor;
import com.miaoyu.ticket.auth.application.RoleCode;
import com.miaoyu.ticket.profile.application.ProfileBehaviorRecorder;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

/** Mock 适配测试证明确认、并发胜者读取和未知恢复都不会重发建单。 */
class AgentConfirmationServiceTest {
    private static final LocalDateTime NOW = LocalDateTime.of(2026, 8, 5, 10, 0);

    @Test
    void shouldInvokeCreateOrderOnlyForTheCasWinner() {
        InMemoryRepository repository = new InMemoryRepository(action());
        CountingTool tool = new CountingTool(success("order-1"));
        AgentConfirmationService service = service(repository, tool, true);

        AgentConfirmationResult first = service.confirm("action-1", true, "trace-1");
        AgentConfirmationResult repeated = service.confirm("action-1", true, "trace-1");

        assertEquals(AgentConfirmationActionStatus.SUCCEEDED, first.action().status());
        assertTrue(first.writeToolInvoked());
        assertEquals(AgentConfirmationActionStatus.SUCCEEDED, repeated.action().status());
        assertFalse(repeated.writeToolInvoked());
        assertEquals(1, tool.executeCalls);
    }

    @Test
    void shouldNotInvokeWriteToolWhenParametersChangeOrUserRejects() {
        CountingTool tool = new CountingTool(success("order-1"));
        AgentConfirmationService changed = service(new InMemoryRepository(action()), tool, false);
        AgentConfirmationResult invalid = changed.confirm("action-1", true, "trace-1");

        AgentConfirmationService rejected = service(new InMemoryRepository(action()), tool, true);
        AgentConfirmationResult declined = rejected.confirm("action-1", false, "trace-1");

        assertEquals(AgentConfirmationValidationFailure.BUSINESS_DATA_INVALID, invalid.validationFailure());
        assertEquals(AgentConfirmationActionStatus.INVALIDATED, invalid.action().status());
        assertEquals(AgentConfirmationActionStatus.REJECTED, declined.action().status());
        assertEquals(0, tool.executeCalls);
    }

    @Test
    void shouldRecordOnlyCasSavedAcceptedAndRejectedFeedbackUsingUtcTime() {
        ProfileBehaviorRecorder recorder = Mockito.mock(ProfileBehaviorRecorder.class);
        AgentConfirmationService accepted = service(new InMemoryRepository(action()),
                new CountingTool(success("order")),
                true, recorder);
        accepted.confirm("action-1", true, "trace");
        Mockito.verify(recorder).recordPlanAccepted("action-1", "plan-1",
                LocalDateTime.of(2026, 8, 5, 2, 0, 2));
        AgentConfirmationService rejected = service(new InMemoryRepository(action()),
                new CountingTool(success("order")),
                true, recorder);
        rejected.confirm("action-1", false, "trace");
        Mockito.verify(recorder).recordPlanRejected("action-1", "plan-1",
                LocalDateTime.of(2026, 8, 5, 2, 0, 2));
    }

    @Test
    void shouldRecoverUnknownOnlyWithOriginalIdentifiersWithoutExecutingAgain() {
        InMemoryRepository repository = new InMemoryRepository(action());
        CountingTool tool = new CountingTool(processing());
        AgentConfirmationService service = service(repository, tool, true);

        AgentConfirmationResult unknown = service.confirm("action-1", true, "trace-1");
        tool.queryResult = success("order-1");
        AgentConfirmationResult recovered = service.recover("action-1", "trace-1");

        assertEquals(AgentConfirmationActionStatus.RESULT_UNKNOWN, unknown.action().status());
        assertEquals(AgentConfirmationActionStatus.SUCCEEDED, recovered.action().status());
        assertEquals(1, tool.executeCalls);
        assertEquals(1, tool.queryCalls);
        assertEquals(tool.firstContext.clientRequestId(), tool.queryContext.clientRequestId());
        assertEquals(tool.firstContext.idempotencyKey(), tool.queryContext.idempotencyKey());
    }

    @Test
    void shouldAllowOnlyOneConcurrentConfirmationToEnterWriteTool() throws Exception {
        InMemoryRepository repository = new InMemoryRepository(action());
        CountingTool tool = new CountingTool(success("order-1"));
        tool.executionStarted = new CountDownLatch(1);
        tool.allowExecutionToFinish = new CountDownLatch(1);
        AgentConfirmationService service = service(repository, tool, true);
        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            var winner = executor.submit(() -> service.confirm("action-1", true, "trace-1"));
            assertTrue(tool.executionStarted.await(3, TimeUnit.SECONDS));

            AgentConfirmationResult loser = service.confirm("action-1", true, "trace-1");
            assertEquals(AgentConfirmationActionStatus.EXECUTING, loser.action().status());
            assertFalse(loser.writeToolInvoked());
            assertEquals(1, tool.executeCalls);

            tool.allowExecutionToFinish.countDown();
            assertEquals(AgentConfirmationActionStatus.SUCCEEDED, winner.get(3, TimeUnit.SECONDS).action().status());
        } finally {
            executor.shutdownNow();
        }
    }

    @Test
    void shouldNotTreatTheCasLoserAsWinnerWhenItReadsTheSameExecutingVersion() throws Exception {
        StaleReadRaceRepository repository = new StaleReadRaceRepository(action());
        CountingTool tool = new CountingTool(success("order-1"));
        tool.executionStarted = new CountDownLatch(1);
        tool.allowExecutionToFinish = new CountDownLatch(1);
        AgentConfirmationService service = service(repository, tool, true);
        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            var first = executor.submit(() -> service.confirm("action-1", true, "trace-1"));
            var second = executor.submit(() -> service.confirm("action-1", true, "trace-2"));

            assertTrue(tool.executionStarted.await(3, TimeUnit.SECONDS));
            assertTrue(repository.loserReadWinner.await(3, TimeUnit.SECONDS));
            assertEquals(1, tool.executeCalls);
            tool.allowExecutionToFinish.countDown();

            List<AgentConfirmationResult> results = List.of(
                    first.get(3, TimeUnit.SECONDS), second.get(3, TimeUnit.SECONDS));
            assertEquals(1, results.stream().filter(AgentConfirmationResult::writeToolInvoked).count());
            assertEquals(1, results.stream().filter(result -> result.action().status()
                    == AgentConfirmationActionStatus.SUCCEEDED).count());
            assertEquals(1, results.stream().filter(result -> result.action().status()
                    == AgentConfirmationActionStatus.EXECUTING).count());
            assertEquals(1, tool.executeCalls);
        } finally {
            tool.allowExecutionToFinish.countDown();
            executor.shutdownNow();
        }
    }

    @Test
    void shouldKeepCreateOrderFixturesFreeOfSensitiveOrWriteKeyFields() throws Exception {
        ObjectMapper objectMapper = new ObjectMapper();
        for (String name : List.of("success", "failed", "result-unknown")) {
            try (var input = getClass().getResourceAsStream("/fixtures/agent/b/create-order-" + name + ".json")) {
                JsonNode fixture = objectMapper.readTree(input);
                assertFalse(fixture.has("userId"));
                assertFalse(fixture.has("idempotencyKey"));
                assertFalse(fixture.has("parameterHash"));
                assertFalse(fixture.has("orderNo"));
                assertFalse(fixture.has("totalAmount"));
            }
        }
    }

    @Test
    void shouldKeepCConfirmedCardPayloadSafeAndKeepPlanVersionAtEventLevel() throws Exception {
        ObjectMapper objectMapper = new ObjectMapper();
        try (var input = getClass().getResourceAsStream("/fixtures/agent/c/order-confirm-card.json")) {
            JsonNode event = objectMapper.readTree(input);
            JsonNode payload = event.path("payload");

            assertEquals(2, event.path("planVersion").asInt());
            assertFalse(payload.has("planVersion"));
            assertEquals("CREATE_ORDER", payload.path("actionType").asText());
            assertFalse(payload.has("seatIds"));
            assertFalse(payload.has("totalAmount"));
            assertFalse(payload.has("idempotencyKey"));
        }
    }

    @Test
    void shouldExposeOnlyCConfirmedFieldsInActionResponse() {
        AgentActionResponse response = new AgentActionResponse(
                "action-1",
                "run-1",
                2,
                AgentConfirmationCardStatus.EXECUTING,
                OffsetDateTime.parse("2026-08-05T16:30:00+08:00"));

        assertEquals("action-1", response.actionId());
        assertEquals("EXECUTING", response.status().name());
        assertEquals(5, AgentActionResponse.class.getRecordComponents().length);
    }

    @Test
    void shouldAuthorizeOnlyTheExecutingActionWithMatchingContextAndHash() {
        AgentConfirmationAction action = action().claim(
                AgentActionWriteIdentifiers.forAction("action-1"), NOW.plusSeconds(1));
        InMemoryRepository repository = new InMemoryRepository(action);
        AgentActionAuthorizationService authorizationService = new AgentActionAuthorizationService(
                repository,
                current -> new AgentActionAuthorizationFacts(
                        current.runId(), current.planId(), current.planVersion(), current.nodeId(),
                        current.command().toolName(), current.parameterHash(), true),
                () -> new CurrentUser(9L, RoleCode.USER, 1L));
        ToolContext context = new ToolContext(
                action.runId(), action.nodeId(), action.command().toolName(), List.of(), 3_000L, "trace-1",
                action.writeIdentifiers().clientRequestId(),
                action.writeIdentifiers().idempotencyKey(),
                action.version());

        assertDoesNotThrow(() -> authorizationService.authorize(
                new AgentActionAuthorizationRequest("action-1", context, "70001", List.of("2", "4"))));
        assertThrows(AgentActionAuthorizationDeniedException.class, () -> authorizationService.authorize(
                new AgentActionAuthorizationRequest("action-1", context, "70001", List.of("2", "5"))));
    }

    private static AgentConfirmationService service(
            AgentConfirmationActionRepository repository,
            CountingTool tool,
            boolean businessDataValid) {
        AgentConfirmationFactsProvider facts = (action, userId) -> new AgentConfirmationValidationContext(
                userId,
                AgentRunStatus.RUNNING,
                action.planId(),
                action.planVersion(),
                PlanNodeStatus.WAITING_CONFIRMATION,
                action.parameterHash(),
                businessDataValid,
                NOW.plusSeconds(1));
        CurrentUserAccessor userAccessor = () -> new CurrentUser(9L, RoleCode.USER, 1L);
        Clock clock = Clock.fixed(Instant.parse("2026-08-05T02:00:02Z"), ZoneId.of("Asia/Shanghai"));
        return new AgentConfirmationService(repository, facts, tool, action -> { }, userAccessor, clock);
    }

    private static AgentConfirmationService service(AgentConfirmationActionRepository repository, CountingTool tool,
            boolean businessDataValid, ProfileBehaviorRecorder recorder) {
        AgentConfirmationFactsProvider facts = (action, userId) -> new AgentConfirmationValidationContext(userId,
                AgentRunStatus.RUNNING, action.planId(), action.planVersion(), PlanNodeStatus.WAITING_CONFIRMATION,
                action.parameterHash(), businessDataValid, NOW.plusSeconds(1));
        return new AgentConfirmationService(repository, facts, tool, action -> { },
                () -> new CurrentUser(9L, RoleCode.USER, 1L),
                Clock.fixed(Instant.parse("2026-08-05T02:00:02Z"), ZoneId.of("Asia/Shanghai")), recorder);
    }

    private static AgentConfirmationAction action() {
        return AgentConfirmationAction.pending(
                1L, "action-1", 9L, 10L, 11L, "run-1", "plan-1", 2, "confirm-order",
                new ConfirmedOrderCommand("createOrder", "70001", List.of("2", "4")), NOW.plusMinutes(5), NOW);
    }

    private static ToolResult<CreateOrderToolResult> success(String reference) {
        return new ToolResult<>(ToolStatus.SUCCESS, new CreateOrderToolResult(reference), null, false, false,
                null, false, null, null, null, null);
    }

    private static ToolResult<CreateOrderToolResult> processing() {
        return new ToolResult<>(ToolStatus.PROCESSING, null, null, false, false,
                "结果确认中", false, null, null, null, null);
    }

    private static final class InMemoryRepository implements AgentConfirmationActionRepository {
        private final Map<String, AgentConfirmationAction> actions = new java.util.concurrent.ConcurrentHashMap<>();

        private InMemoryRepository(AgentConfirmationAction action) {
            actions.put(action.actionId(), action);
        }

        @Override
        public Optional<AgentConfirmationAction> findByActionId(String actionId) {
            return Optional.ofNullable(actions.get(actionId));
        }

        @Override
        public Optional<AgentConfirmationAction> findByActionIdForUpdate(String actionId) {
            return findByActionId(actionId);
        }

        @Override
        public Optional<AgentConfirmationAction> findByCreationKey(
                long userId,
                long agentRunId,
                String planId,
                int planVersion,
                String nodeId,
                String toolName,
                String parameterHash) {
            return actions.values().stream().filter(action -> action.userId() == userId
                    && action.agentRunId() == agentRunId
                    && action.planId().equals(planId)
                    && action.planVersion() == planVersion
                    && action.nodeId().equals(nodeId)
                    && action.command().toolName().equals(toolName)
                    && action.parameterHash().value().equals(parameterHash)).findFirst();
        }

        @Override
        public Optional<AgentConfirmationAction> findByCreationKeyForUpdate(
                long userId,
                long agentRunId,
                String planId,
                int planVersion,
                String nodeId,
                String toolName,
                String parameterHash) {
            return findByCreationKey(userId, agentRunId, planId, planVersion, nodeId, toolName, parameterHash);
        }

        @Override
        public void insert(AgentConfirmationAction action) {
            actions.put(action.actionId(), action);
        }

        @Override
        public boolean compareAndSet(
                String actionId,
                long expectedVersion,
                AgentConfirmationActionStatus expectedStatus,
                AgentConfirmationAction next) {
            AtomicBoolean updated = new AtomicBoolean(false);
            actions.compute(actionId, (ignored, current) -> {
                if (current == null || current.version() != expectedVersion || current.status() != expectedStatus) {
                    return current;
                }
                updated.set(true);
                return next;
            });
            return updated.get();
        }
    }

    /** 模拟两个请求都在 CAS 前读到 PENDING，而失败者随后读到胜者 EXECUTING(v+1) 的 MySQL 场景。 */
    private static final class StaleReadRaceRepository implements AgentConfirmationActionRepository {
        private final AgentConfirmationAction initial;
        private final CountDownLatch initialReads = new CountDownLatch(2);
        private final CountDownLatch loserReadWinner = new CountDownLatch(1);
        private final java.util.concurrent.atomic.AtomicInteger readCount =
                new java.util.concurrent.atomic.AtomicInteger();
        private AgentConfirmationAction stored;

        private StaleReadRaceRepository(AgentConfirmationAction initial) {
            this.initial = initial;
            this.stored = initial;
        }

        @Override
        public Optional<AgentConfirmationAction> findByActionId(String actionId) {
            if (readCount.getAndIncrement() < 2) {
                initialReads.countDown();
                try {
                    if (!initialReads.await(3, TimeUnit.SECONDS)) {
                        throw new IllegalStateException("并发初始读取未同时到达");
                    }
                } catch (InterruptedException exception) {
                    Thread.currentThread().interrupt();
                    throw new IllegalStateException("并发读取测试线程被中断", exception);
                }
                return Optional.of(initial);
            }
            synchronized (this) {
                loserReadWinner.countDown();
                return Optional.of(stored);
            }
        }

        @Override
        public Optional<AgentConfirmationAction> findByActionIdForUpdate(String actionId) {
            return findByActionId(actionId);
        }

        @Override
        public Optional<AgentConfirmationAction> findByCreationKey(
                long userId,
                long agentRunId,
                String planId,
                int planVersion,
                String nodeId,
                String toolName,
                String parameterHash) {
            return Optional.empty();
        }

        @Override
        public Optional<AgentConfirmationAction> findByCreationKeyForUpdate(
                long userId,
                long agentRunId,
                String planId,
                int planVersion,
                String nodeId,
                String toolName,
                String parameterHash) {
            return Optional.empty();
        }

        @Override
        public void insert(AgentConfirmationAction action) {
            throw new UnsupportedOperationException("本测试不创建 action");
        }

        @Override
        public synchronized boolean compareAndSet(
                String actionId,
                long expectedVersion,
                AgentConfirmationActionStatus expectedStatus,
                AgentConfirmationAction next) {
            if (stored.version() != expectedVersion || stored.status() != expectedStatus) {
                return false;
            }
            stored = next;
            return true;
        }
    }

    private static final class CountingTool implements CreateOrderToolAdapter {
        private ToolResult<CreateOrderToolResult> executeResult;
        private ToolResult<CreateOrderToolResult> queryResult = processing();
        private int executeCalls;
        private int queryCalls;
        private ToolContext firstContext;
        private ToolContext queryContext;
        private CountDownLatch executionStarted;
        private CountDownLatch allowExecutionToFinish;

        private CountingTool(ToolResult<CreateOrderToolResult> executeResult) {
            this.executeResult = executeResult;
        }

        @Override
        public ToolResult<CreateOrderToolResult> execute(
                String actionId, ToolContext context, ConfirmedOrderCommand command) {
            executeCalls++;
            firstContext = context;
            if (executionStarted != null) {
                executionStarted.countDown();
                try {
                    if (!allowExecutionToFinish.await(3, TimeUnit.SECONDS)) {
                        throw new IllegalStateException("测试等待建单 Mock 超时");
                    }
                } catch (InterruptedException exception) {
                    Thread.currentThread().interrupt();
                    throw new IllegalStateException("测试线程被中断", exception);
                }
            }
            return executeResult;
        }

        @Override
        public ToolResult<CreateOrderToolResult> queryByOriginalIdentifiers(
                ToolContext context,
                ConfirmedOrderCommand command) {
            queryCalls++;
            queryContext = context;
            return queryResult;
        }
    }
}
