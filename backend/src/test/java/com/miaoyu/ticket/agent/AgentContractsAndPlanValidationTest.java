package com.miaoyu.ticket.agent;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.miaoyu.ticket.agent.application.model.PlanGenerationRequest;
import com.miaoyu.ticket.agent.application.model.ReplyGenerationRequest;
import com.miaoyu.ticket.agent.application.reply.AgentReplyMessageType;
import com.miaoyu.ticket.agent.application.reply.ErrorReplyFacts;
import com.miaoyu.ticket.agent.application.tool.AgentToolDefinitions;
import com.miaoyu.ticket.agent.domain.plan.CandidatePlan;
import com.miaoyu.ticket.agent.domain.plan.CandidatePlanNode;
import com.miaoyu.ticket.agent.domain.plan.FailurePolicy;
import com.miaoyu.ticket.agent.domain.plan.InputReference;
import com.miaoyu.ticket.agent.domain.plan.InputReferenceSource;
import com.miaoyu.ticket.agent.domain.plan.PlanNodeStatus;
import com.miaoyu.ticket.agent.domain.plan.PlanNodeType;
import com.miaoyu.ticket.agent.domain.plan.PlanSchemaValidator;
import com.miaoyu.ticket.agent.domain.plan.PlanValidationContext;
import com.miaoyu.ticket.agent.domain.plan.PlanValidationIssueCode;
import com.miaoyu.ticket.agent.domain.plan.SlotSnapshot;
import com.miaoyu.ticket.agent.domain.tool.ToolCommand;
import com.miaoyu.ticket.agent.domain.tool.ToolContext;
import com.miaoyu.ticket.agent.domain.tool.ToolDefinition;
import com.miaoyu.ticket.agent.domain.tool.ToolInputDefinition;
import com.miaoyu.ticket.agent.domain.tool.ToolRegistry;
import com.miaoyu.ticket.agent.domain.tool.ToolResult;
import com.miaoyu.ticket.agent.domain.tool.ToolStatus;
import com.miaoyu.ticket.agent.infrastructure.model.MockModelGateway;
import java.lang.reflect.RecordComponent;
import java.time.Duration;
import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;

/** Agent 工具协议、计划校验和 Mock 网关的纯单元测试。 */
class AgentContractsAndPlanValidationTest {

    @Test
    void shouldKeepToolContextAndResultFieldsStable() {
        Set<String> contextFields = recordFieldNames(ToolContext.class);
        assertEquals(
                Set.of(
                        "runId",
                        "nodeId",
                        "targetName",
                        "inputRefs",
                        "deadlineMs",
                        "traceId",
                        "clientRequestId",
                        "idempotencyKey",
                        "stateVersion"),
                contextFields);
        Set<String> resultFields = recordFieldNames(ToolResult.class);
        assertFalse(resultFields.contains("dataTime"));
        assertFalse(resultFields.contains("recommendReplan"));
        assertFalse(resultFields.contains("suggestedNextStep"));
        Set<String> candidateFields = recordFieldNames(CandidatePlanNode.class);
        assertFalse(candidateFields.contains("businessParameterHash"));
        assertFalse(candidateFields.contains("actionId"));
    }

    @Test
    void shouldRejectInvalidToolContextAndRequireWriteIdentifiers() {
        assertThrows(
                IllegalArgumentException.class,
                () -> new ToolContext("run", "node", "tool", List.of(), 0L, "trace", null, null, null));
        ToolContext readContext = new ToolContext("run", "node", "tool", List.of(), 100L, "trace", null, null, null);
        ToolDefinition writeTool = writeTool();
        assertThrows(IllegalArgumentException.class, () -> writeTool.validateContext(readContext));
        ToolContext writeContext = new ToolContext(
                "run", "node", "writeTool", List.of(), 100L, "trace", "request-1", "idem-1", 1L);
        writeTool.validateContext(writeContext);
    }

    @Test
    void shouldValidateToolResultFreshnessAndSuccessErrorCode() {
        Instant dataAt = Instant.parse("2026-08-03T00:00:00Z");
        ToolResult<String> result = new ToolResult<>(
                ToolStatus.SUCCESS,
                "data",
                null,
                false,
                false,
                null,
                false,
                null,
                null,
                dataAt,
                dataAt.plusSeconds(60L));
        assertTrue(result.hasFreshnessWindow());
        assertThrows(
                IllegalArgumentException.class,
                () -> new ToolResult<>(
                        ToolStatus.SUCCESS,
                        "data",
                        101001,
                        false,
                        false,
                        null,
                        false,
                        null,
                        null,
                        null,
                        null));
        assertThrows(
                IllegalArgumentException.class,
                () -> new ToolResult<>(
                        ToolStatus.FAILED,
                        null,
                        301001,
                        false,
                        true,
                        "ASK_USER",
                        false,
                        null,
                        null,
                        dataAt,
                        null));
    }

    @Test
    void shouldBuildImmutableToolRegistryAndRejectInvalidDefinitions() {
        ToolDefinition readTool = readTool();
        ToolRegistry registry = new ToolRegistry(List.of(readTool));
        assertTrue(registry.find("readTool").isPresent());
        assertTrue(registry.find("unknownTool").isEmpty());
        assertThrows(UnsupportedOperationException.class, () -> registry.definitions().put("new", readTool));
        assertThrows(IllegalArgumentException.class, () -> new ToolRegistry(List.of(readTool, readTool)));
        assertThrows(
                IllegalArgumentException.class,
                () -> new ToolDefinition(
                        "writeWithoutIdempotency",
                        WriteCommand.class,
                        String.class,
                        false,
                        Duration.ofSeconds(3L),
                        false,
                        List.of(),
                        Set.of()));
    }

    @Test
    void shouldAcceptLegalCandidatePlanAndCreateServerOwnedExecutionFields() {
        PlanSchemaValidator validator = new PlanSchemaValidator(new ToolRegistry(List.of(readTool())));
        CandidatePlan candidatePlan = new CandidatePlan(
                "plan-1",
                1,
                List.of(new CandidatePlanNode(
                        "read",
                        PlanNodeType.CALL_TOOL,
                        "readTool",
                        List.of(new InputReference("movieId", InputReferenceSource.SLOT, "movieId")),
                        List.of(),
                        FailurePolicy.RETRY_ONCE)));
        PlanValidationContext context = new PlanValidationContext(
                Map.of("movieId", String.class),
                Map.of(),
                new SlotSnapshot(4L, Map.of("movieId", "m_1")));

        var result = validator.validate(candidatePlan, context);

        assertTrue(result.isValid());
        var executionNode = result.executionPlan().orElseThrow().nodes().getFirst();
        assertEquals(PlanNodeStatus.PENDING, executionNode.status());
        assertFalse(executionNode.requiresConfirmation());
        assertFalse(executionNode.autoSkipped());
        assertEquals(4L, executionNode.slotSnapshot().version());
    }

    @Test
    void shouldReportMultipleBasicAndDependencyProblems() {
        PlanSchemaValidator validator = new PlanSchemaValidator(new ToolRegistry(List.of()));
        CandidatePlan candidatePlan = new CandidatePlan(
                "plan-2",
                0,
                List.of(
                        new CandidatePlanNode(
                                "first",
                                PlanNodeType.COMPUTE,
                                null,
                                List.of(),
                                List.of("second"),
                                FailurePolicy.FAIL),
                        new CandidatePlanNode(
                                "second",
                                PlanNodeType.COMPUTE,
                                null,
                                List.of(),
                                List.of("first"),
                                FailurePolicy.FAIL),
                        new CandidatePlanNode(
                                "first",
                                PlanNodeType.COMPUTE,
                                null,
                                List.of(),
                                List.of(),
                                FailurePolicy.FAIL)));

        var result = validator.validate(candidatePlan, emptyContext());

        assertFalse(result.isValid());
        assertTrue(hasIssue(result, PlanValidationIssueCode.PLAN_VERSION_INVALID));
        assertTrue(hasIssue(result, PlanValidationIssueCode.NODE_ID_DUPLICATE));
        assertTrue(hasIssue(result, PlanValidationIssueCode.DEPENDENCY_CYCLE));
        assertTrue(result.executionPlan().isEmpty());
    }

    @Test
    void shouldRejectUnknownMissingMismatchedAndNonUpstreamToolInputs() {
        PlanSchemaValidator validator = new PlanSchemaValidator(new ToolRegistry(List.of(readTool())));
        CandidatePlan candidatePlan = new CandidatePlan(
                "plan-3",
                1,
                List.of(
                        new CandidatePlanNode(
                                "future",
                                PlanNodeType.COMPUTE,
                                null,
                                List.of(),
                                List.of(),
                                FailurePolicy.FAIL),
                        new CandidatePlanNode(
                                "read",
                                PlanNodeType.CALL_TOOL,
                                "readTool",
                                List.of(
                                        new InputReference("movieId", InputReferenceSource.SLOT, "wrongType"),
                                        new InputReference("extra", InputReferenceSource.SLOT, "movieId"),
                                new InputReference("movieId", InputReferenceSource.NODE_RESULT, "future")),
                                List.of(),
                                FailurePolicy.RETRY_ONCE),
                        new CandidatePlanNode(
                                "unknown",
                                PlanNodeType.CALL_TOOL,
                                "missingTool",
                                List.of(),
                                List.of(),
                                FailurePolicy.FAIL)));
        PlanValidationContext context = new PlanValidationContext(
                Map.of("wrongType", Integer.class, "movieId", String.class),
                Map.of("future", String.class),
                new SlotSnapshot(0L, Map.of()));

        var result = validator.validate(candidatePlan, context);

        assertFalse(result.isValid());
        assertTrue(hasIssue(result, PlanValidationIssueCode.INPUT_TYPE_MISMATCH));
        assertTrue(hasIssue(result, PlanValidationIssueCode.TOOL_INPUT_UNKNOWN));
        assertTrue(hasIssue(result, PlanValidationIssueCode.INPUT_REFERENCE_NOT_UPSTREAM));
        assertTrue(hasIssue(result, PlanValidationIssueCode.TOOL_NOT_FOUND));
    }

    @Test
    void shouldAcceptWritePlanOnlyAsConfirmationGatedNode() {
        PlanSchemaValidator validator = new PlanSchemaValidator(new ToolRegistry(List.of(writeTool())));
        CandidatePlan candidatePlan = new CandidatePlan(
                "plan-4",
                1,
                List.of(
                        node(
                                "validate",
                                PlanNodeType.VALIDATE,
                                null,
                                List.of(),
                                List.of(),
                                FailurePolicy.FAIL),
                        node(
                                "confirm",
                                PlanNodeType.CONFIRM_ACTION,
                                null,
                                List.of(),
                                List.of("validate"),
                                FailurePolicy.FAIL),
                        node(
                                "write",
                                PlanNodeType.CALL_TOOL,
                                "writeTool",
                                List.of(new InputReference("showId", InputReferenceSource.SLOT, "showId")),
                                List.of("validate", "confirm"),
                                FailurePolicy.FAIL)));

        var result = validator.validate(
                candidatePlan,
                new PlanValidationContext(Map.of("showId", String.class), Map.of(), new SlotSnapshot(1L, Map.of())));

        assertTrue(result.isValid());
        assertTrue(result.executionPlan().orElseThrow().nodes().getLast().requiresConfirmation());
    }

    @Test
    void shouldRejectWritePlanBeforeAnyModelConfirmationValueCanBeTrusted() {
        PlanSchemaValidator validator = new PlanSchemaValidator(new ToolRegistry(List.of(writeTool())));
        CandidatePlan candidatePlan = new CandidatePlan(
                "plan-5",
                1,
                List.of(node(
                        "write",
                        PlanNodeType.CALL_TOOL,
                        "writeTool",
                        List.of(new InputReference("showId", InputReferenceSource.SLOT, "showId")),
                        List.of(),
                        FailurePolicy.RETRY_ONCE)));

        var result = validator.validate(
                candidatePlan,
                new PlanValidationContext(Map.of("showId", String.class), Map.of(), new SlotSnapshot(1L, Map.of())));

        assertFalse(result.isValid());
        assertTrue(hasIssue(result, PlanValidationIssueCode.WRITE_TOOL_CONFIRMATION_REQUIRED));
        assertTrue(result.executionPlan().isEmpty());
    }

    @Test
    void shouldKeepMockPlanAndReplyDeterministicAndAlwaysValidatePlan() {
        ToolRegistry registry = new ToolRegistry(List.of(AgentToolDefinitions.rankMoviePlan()));
        PlanSchemaValidator validator = new PlanSchemaValidator(registry);
        MockModelGateway gateway = new MockModelGateway(validator, registry);
        Map<String, String> slots = Map.of(
                "movieId", "101", "cinemaId", "201", "date", "2026-08-04");
        PlanGenerationRequest request = new PlanGenerationRequest(
                "request-1", "帮我找电影", slots, Set.of("rankMoviePlan"));

        var first = gateway.generatePlan(request);
        var second = gateway.generatePlan(request);
        var changedAllowList = gateway.generatePlan(
                new PlanGenerationRequest("request-1", "帮我找电影", slots, Set.of("otherTool")));

        assertEquals(first.candidatePlan(), second.candidatePlan());
        assertTrue(first.validationResult().isValid());
        assertNotEquals(first.candidatePlan().planId(), changedAllowList.candidatePlan().planId());
        ReplyGenerationRequest replyRequest = new ReplyGenerationRequest(
                "request-1",
                "帮我找电影",
                AgentReplyMessageType.ERROR,
                new ErrorReplyFacts(100001, List.of()));
        assertEquals(
                gateway.generateReply(replyRequest),
                gateway.generateReply(replyRequest));
    }

    private static CandidatePlanNode node(
            String nodeId,
            PlanNodeType type,
            String targetName,
            List<InputReference> inputReferences,
            List<String> dependsOn,
            FailurePolicy failurePolicy) {
        return new CandidatePlanNode(
                nodeId,
                type,
                targetName,
                inputReferences,
                dependsOn,
                failurePolicy);
    }

    private static ToolDefinition readTool() {
        return new ToolDefinition(
                "readTool",
                ReadCommand.class,
                String.class,
                true,
                Duration.ofSeconds(3L),
                false,
                List.of(new ToolInputDefinition("movieId", String.class, true)),
                Set.of(301001));
    }

    private static ToolDefinition writeTool() {
        return new ToolDefinition(
                "writeTool",
                WriteCommand.class,
                String.class,
                false,
                Duration.ofSeconds(3L),
                true,
                List.of(new ToolInputDefinition("showId", String.class, true)),
                Set.of(301002));
    }

    private static PlanValidationContext emptyContext() {
        return new PlanValidationContext(Map.of(), Map.of(), new SlotSnapshot(0L, Map.of()));
    }

    private static boolean hasIssue(
            com.miaoyu.ticket.agent.domain.plan.PlanValidationResult result, PlanValidationIssueCode code) {
        return result.issues().stream().anyMatch(issue -> issue.code() == code);
    }

    private static Set<String> recordFieldNames(Class<?> type) {
        return Arrays.stream(type.getRecordComponents()).map(RecordComponent::getName).collect(Collectors.toSet());
    }

    private record ReadCommand(String movieId) implements ToolCommand {
    }

    private record WriteCommand(String showId) implements ToolCommand {
    }
}
