package com.miaoyu.ticket.agent;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.never;

import com.miaoyu.ticket.agent.application.model.ModelGateway;
import com.miaoyu.ticket.agent.application.model.AgentIntent;
import com.miaoyu.ticket.agent.application.model.PlanGenerationRequest;
import com.miaoyu.ticket.agent.application.model.PlanGenerationResponse;
import com.miaoyu.ticket.agent.application.model.ReplyGenerationResponse;
import com.miaoyu.ticket.agent.application.reply.AgentReplyMessageType;
import com.miaoyu.ticket.agent.application.reply.TextReplyFacts;
import com.miaoyu.ticket.agent.application.run.MultiToolSupervisor;
import com.miaoyu.ticket.agent.application.run.MultiToolSupervisorRequest;
import com.miaoyu.ticket.agent.application.run.MultiToolSupervisorResult;
import com.miaoyu.ticket.agent.application.run.ProfileContextPrefetcher;
import com.miaoyu.ticket.agent.application.tool.AgentToolDefinitions;
import com.miaoyu.ticket.agent.application.tool.RankMoviePlanExecutionAdapter;
import com.miaoyu.ticket.agent.application.tool.RankMoviePlanExecutionRequest;
import com.miaoyu.ticket.agent.application.tool.RankMoviePlanExecutionResult;
import com.miaoyu.ticket.agent.application.tool.ReadOnlyToolExecutionAdapter;
import com.miaoyu.ticket.agent.domain.plan.CandidatePlan;
import com.miaoyu.ticket.agent.domain.plan.CandidatePlanNode;
import com.miaoyu.ticket.agent.domain.plan.FailurePolicy;
import com.miaoyu.ticket.agent.domain.plan.InputReference;
import com.miaoyu.ticket.agent.domain.plan.InputReferenceSource;
import com.miaoyu.ticket.agent.domain.plan.PlanNodeType;
import com.miaoyu.ticket.agent.domain.plan.PlanSchemaValidator;
import com.miaoyu.ticket.agent.domain.plan.PlanValidationContext;
import com.miaoyu.ticket.agent.domain.plan.SlotSnapshot;
import com.miaoyu.ticket.agent.domain.run.ExecutionPlanStateMachine;
import com.miaoyu.ticket.agent.domain.tool.ToolRegistry;
import com.miaoyu.ticket.agent.domain.tool.ToolResult;
import com.miaoyu.ticket.agent.domain.tool.ToolStatus;
import com.miaoyu.ticket.recommendation.api.RankMoviePlanTool;
import com.miaoyu.ticket.recommendation.application.FixedRecommendationResult;
import com.miaoyu.ticket.profile.application.ProfileSummary;
import com.miaoyu.ticket.profile.domain.ProfileTagPolarity;
import com.miaoyu.ticket.profile.domain.ProfileTagSource;
import com.miaoyu.ticket.profile.domain.ProfileTagType;
import com.miaoyu.ticket.profile.infrastructure.tool.GetProfileSummaryTool;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

class MultiToolSupervisorTest {
    @Test
    void shouldUseTextReplyWithoutPlanOrToolForGeneralChat() {
        ToolRegistry registry = new ToolRegistry(List.of(AgentToolDefinitions.rankMoviePlan()));
        PlanSchemaValidator validator = new PlanSchemaValidator(registry);
        ExecutionPlanStateMachine stateMachine = new ExecutionPlanStateMachine(registry);
        ModelGateway gateway = movieGateway();
        RankMoviePlanExecutionAdapter adapter = Mockito.mock(RankMoviePlanExecutionAdapter.class);
        when(adapter.targetName()).thenReturn(RankMoviePlanTool.TARGET_NAME);
        when(adapter.definition()).thenReturn(AgentToolDefinitions.rankMoviePlan());
        when(gateway.classifyIntent(any())).thenReturn(AgentIntent.GENERAL_CHAT);
        when(gateway.generateReply(any())).thenReturn(new ReplyGenerationResponse(
                "这是 CineWise 的观影助手。", AgentReplyMessageType.TEXT, new TextReplyFacts()));
        MultiToolSupervisor supervisor = new MultiToolSupervisor(gateway, registry, validator, stateMachine,
                List.of(adapter));

        var result = supervisor.run(new MultiToolSupervisorRequest(
                "general-1", "这啥", context(), "run-general", "trace-general", 3_000L));

        assertThat(result.validation().isValid()).isTrue();
        assertThat(result.toolResults()).isEmpty();
        assertThat(result.safeNextAction()).isEqualTo("GENERAL_CHAT");
        assertThat(result.generatedReply().messageType()).isEqualTo(AgentReplyMessageType.TEXT);
        Mockito.verify(gateway, never()).generatePlan(any());
        Mockito.verify(adapter, never()).execute(any(ReadOnlyToolExecutionAdapter.ExecutionRequest.class));
    }

    @Test
    void shouldForwardValidatedGeneralChatDeltasWithoutASecondHoldback() {
        ToolRegistry registry = new ToolRegistry(List.of(AgentToolDefinitions.rankMoviePlan()));
        PlanSchemaValidator validator = new PlanSchemaValidator(registry);
        ExecutionPlanStateMachine stateMachine = new ExecutionPlanStateMachine(registry);
        RankMoviePlanExecutionAdapter adapter = Mockito.mock(RankMoviePlanExecutionAdapter.class);
        when(adapter.targetName()).thenReturn(RankMoviePlanTool.TARGET_NAME);
        when(adapter.definition()).thenReturn(AgentToolDefinitions.rankMoviePlan());
        ModelGateway gateway = new ModelGateway() {
            @Override
            public AgentIntent classifyIntent(com.miaoyu.ticket.agent.application.model.IntentClassificationRequest request) {
                return AgentIntent.GENERAL_CHAT;
            }

            @Override
            public PlanGenerationResponse generatePlan(PlanGenerationRequest request) {
                throw new AssertionError("普通对话不应生成计划");
            }

            @Override
            public ReplyGenerationResponse generateReply(
                    com.miaoyu.ticket.agent.application.model.ReplyGenerationRequest request) {
                return new ReplyGenerationResponse("你好，想看什么电影？", AgentReplyMessageType.TEXT,
                        new TextReplyFacts());
            }

            @Override
            public ReplyGenerationResponse generateReplyStream(
                    com.miaoyu.ticket.agent.application.model.ReplyGenerationRequest request,
                    java.util.function.Consumer<String> onTextDelta) {
                onTextDelta.accept("你好，");
                onTextDelta.accept("想看什么电影？");
                return generateReply(request);
            }

            @Override
            public boolean emitsValidatedTextDeltas() {
                return true;
            }
        };
        MultiToolSupervisor supervisor = new MultiToolSupervisor(gateway, registry, validator, stateMachine,
                List.of(adapter));
        List<String> deltas = new java.util.ArrayList<>();

        supervisor.run(new MultiToolSupervisorRequest(
                "general-stream", "你好", context(), "run-general-stream", "trace-general-stream", 3_000L),
                deltas::add);

        assertThat(deltas).containsExactly("你好，", "想看什么电影？");
        Mockito.verify(adapter, never()).execute(any(ReadOnlyToolExecutionAdapter.ExecutionRequest.class));
    }

    @Test
    void shouldReturnTrustedPlanExplanationWithoutClassifyingOrCallingTool() {
        ToolRegistry registry = new ToolRegistry(List.of(AgentToolDefinitions.rankMoviePlan()));
        PlanSchemaValidator validator = new PlanSchemaValidator(registry);
        ExecutionPlanStateMachine stateMachine = new ExecutionPlanStateMachine(registry);
        ModelGateway gateway = Mockito.mock(ModelGateway.class);
        RankMoviePlanExecutionAdapter adapter = Mockito.mock(RankMoviePlanExecutionAdapter.class);
        when(adapter.targetName()).thenReturn(RankMoviePlanTool.TARGET_NAME);
        when(adapter.definition()).thenReturn(AgentToolDefinitions.rankMoviePlan());
        MultiToolSupervisor supervisor = new MultiToolSupervisor(
                gateway, registry, validator, stateMachine, List.of(adapter));
        ReplyGenerationResponse explanation = new ReplyGenerationResponse(
                "第2个方案不需要继续调整。", AgentReplyMessageType.TEXT, new TextReplyFacts());

        var result = supervisor.run(new MultiToolSupervisorRequest(
                "explain-1", "解释第2个方案", context(), "run-explain", "trace-explain", 3_000L,
                null, null, null, null, explanation));

        assertThat(result.generatedReply()).isEqualTo(explanation);
        assertThat(result.toolResults()).isEmpty();
        Mockito.verify(gateway, never()).classifyIntent(any());
        Mockito.verify(gateway, never()).generatePlan(any());
        Mockito.verify(adapter, never()).execute(any(ReadOnlyToolExecutionAdapter.ExecutionRequest.class));
    }

    @Test
    void shouldNotAskForTravelTaskIdWhenTravelIntentHasNoTrustedContext() {
        ToolRegistry registry = new ToolRegistry(List.of(
                AgentToolDefinitions.rankMoviePlan(), AgentToolDefinitions.getTravelAdvice()));
        PlanSchemaValidator validator = new PlanSchemaValidator(registry);
        ExecutionPlanStateMachine stateMachine = new ExecutionPlanStateMachine(registry);
        ModelGateway gateway = movieGateway();
        RankMoviePlanExecutionAdapter adapter = Mockito.mock(RankMoviePlanExecutionAdapter.class);
        when(adapter.targetName()).thenReturn(RankMoviePlanTool.TARGET_NAME);
        when(adapter.definition()).thenReturn(AgentToolDefinitions.rankMoviePlan());
        when(gateway.classifyIntent(any())).thenReturn(AgentIntent.TRAVEL);
        when(gateway.generateReply(any())).thenReturn(new ReplyGenerationResponse(
                "请从已有订单或出行任务入口查看建议。", AgentReplyMessageType.TEXT, new TextReplyFacts()));
        MultiToolSupervisor supervisor = new MultiToolSupervisor(gateway, registry, validator, stateMachine,
                List.of(adapter));

        var result = supervisor.run(new MultiToolSupervisorRequest(
                "travel-1", "帮我看看出行建议", context(), "run-travel", "trace-travel", 3_000L));

        assertThat(result.safeNextAction()).isEqualTo("GENERAL_CHAT");
        assertThat(result.generatedReply().text()).doesNotContainIgnoringCase("travelTaskId");
        assertThat(result.toolResults()).isEmpty();
        Mockito.verify(gateway, never()).generatePlan(any());
    }

    @Test
    void shouldExposeTravelToolOnlyForTrustedServerContext() {
        ToolRegistry registry = new ToolRegistry(List.of(
                AgentToolDefinitions.rankMoviePlan(), AgentToolDefinitions.getTravelAdvice()));
        PlanSchemaValidator validator = new PlanSchemaValidator(registry);
        ExecutionPlanStateMachine stateMachine = new ExecutionPlanStateMachine(registry);
        ModelGateway gateway = movieGateway();
        RankMoviePlanExecutionAdapter adapter = Mockito.mock(RankMoviePlanExecutionAdapter.class);
        when(adapter.targetName()).thenReturn(RankMoviePlanTool.TARGET_NAME);
        when(adapter.definition()).thenReturn(AgentToolDefinitions.rankMoviePlan());
        when(gateway.classifyIntent(any())).thenReturn(AgentIntent.TRAVEL);
        CandidatePlan emptyPlan = new CandidatePlan("travel-plan", 1, List.of());
        PlanValidationContext trustedContext = new PlanValidationContext(
                Map.of("travelTaskId", String.class), Map.of(),
                new SlotSnapshot(2L, Map.of("travelTaskId", "trusted-task")));
        when(gateway.generatePlan(any())).thenReturn(
                new PlanGenerationResponse(emptyPlan, validator.validate(emptyPlan, trustedContext)));
        MultiToolSupervisor supervisor = new MultiToolSupervisor(gateway, registry, validator, stateMachine,
                List.of(adapter));

        supervisor.run(new MultiToolSupervisorRequest(
                "travel-2", "查看已有出行建议", trustedContext, "run-travel-2", "trace-travel-2", 3_000L));

        var captured = org.mockito.ArgumentCaptor.forClass(
                com.miaoyu.ticket.agent.application.model.PlanGenerationRequest.class);
        Mockito.verify(gateway).generatePlan(captured.capture());
        assertThat(captured.getValue().allowedToolNames()).containsExactly("getTravelAdvice");
    }

    @Test
    void shouldUseSafeTextWhenGeneralReplyModelFails() {
        ToolRegistry registry = new ToolRegistry(List.of(AgentToolDefinitions.rankMoviePlan()));
        PlanSchemaValidator validator = new PlanSchemaValidator(registry);
        ExecutionPlanStateMachine stateMachine = new ExecutionPlanStateMachine(registry);
        ModelGateway gateway = movieGateway();
        RankMoviePlanExecutionAdapter adapter = Mockito.mock(RankMoviePlanExecutionAdapter.class);
        when(adapter.targetName()).thenReturn(RankMoviePlanTool.TARGET_NAME);
        when(adapter.definition()).thenReturn(AgentToolDefinitions.rankMoviePlan());
        when(gateway.classifyIntent(any())).thenReturn(AgentIntent.GENERAL_CHAT);
        when(gateway.generateReply(any())).thenThrow(new IllegalStateException("provider detail"));
        MultiToolSupervisor supervisor = new MultiToolSupervisor(gateway, registry, validator, stateMachine,
                List.of(adapter));

        var result = supervisor.run(new MultiToolSupervisorRequest(
                "general-2", "你好", context(), "run-general-2", "trace-general-2", 3_000L));

        assertThat(result.generatedReply().messageType()).isEqualTo(AgentReplyMessageType.TEXT);
        assertThat(result.generatedReply().text()).doesNotContain("provider detail");
        Mockito.verify(gateway, never()).generatePlan(any());
    }

    @Test
    void shouldTreatNullIntentAsGeneralChatWithoutPlanOrTool() {
        ToolRegistry registry = new ToolRegistry(List.of(AgentToolDefinitions.rankMoviePlan()));
        PlanSchemaValidator validator = new PlanSchemaValidator(registry);
        ExecutionPlanStateMachine stateMachine = new ExecutionPlanStateMachine(registry);
        ModelGateway gateway = Mockito.mock(ModelGateway.class);
        RankMoviePlanExecutionAdapter adapter = Mockito.mock(RankMoviePlanExecutionAdapter.class);
        when(adapter.targetName()).thenReturn(RankMoviePlanTool.TARGET_NAME);
        when(adapter.definition()).thenReturn(AgentToolDefinitions.rankMoviePlan());
        when(gateway.generateReply(any())).thenReturn(new ReplyGenerationResponse(
                "你好，我可以帮你找电影。", AgentReplyMessageType.TEXT, new TextReplyFacts()));
        MultiToolSupervisor supervisor = new MultiToolSupervisor(gateway, registry, validator, stateMachine,
                List.of(adapter));

        var result = supervisor.run(new MultiToolSupervisorRequest(
                "null-intent", "这啥", context(), "run-null", "trace-null", 3_000L));

        assertThat(result.generatedReply().messageType()).isEqualTo(AgentReplyMessageType.TEXT);
        Mockito.verify(gateway, never()).generatePlan(any());
        Mockito.verify(adapter, never()).execute(any(ReadOnlyToolExecutionAdapter.ExecutionRequest.class));
    }

    @Test
    void shouldTreatIntentClassificationFailureAsGeneralChatWithoutPlanOrTool() {
        ToolRegistry registry = new ToolRegistry(List.of(
                AgentToolDefinitions.rankMoviePlan(), AgentToolDefinitions.getTravelAdvice()));
        PlanSchemaValidator validator = new PlanSchemaValidator(registry);
        ExecutionPlanStateMachine stateMachine = new ExecutionPlanStateMachine(registry);
        ModelGateway gateway = Mockito.mock(ModelGateway.class);
        when(gateway.classifyIntent(any())).thenThrow(new IllegalStateException("provider timeout"));
        when(gateway.generateReply(any())).thenReturn(new ReplyGenerationResponse(
                "我可以帮你找电影。", AgentReplyMessageType.TEXT, new TextReplyFacts()));
        RankMoviePlanExecutionAdapter adapter = Mockito.mock(RankMoviePlanExecutionAdapter.class);
        when(adapter.targetName()).thenReturn(RankMoviePlanTool.TARGET_NAME);
        when(adapter.definition()).thenReturn(AgentToolDefinitions.rankMoviePlan());
        MultiToolSupervisor supervisor = new MultiToolSupervisor(gateway, registry, validator, stateMachine,
                List.of(adapter));

        var result = supervisor.run(new MultiToolSupervisorRequest(
                "intent-failure", "这啥", context(), "run-intent-failure", "trace-intent-failure", 3_000L));

        assertThat(result.safeNextAction()).isEqualTo("GENERAL_CHAT");
        assertThat(result.generatedReply().messageType()).isEqualTo(AgentReplyMessageType.TEXT);
        assertThat(result.toolResults()).isEmpty();
        Mockito.verify(gateway, never()).generatePlan(any());
        Mockito.verify(adapter, never()).execute(any(ReadOnlyToolExecutionAdapter.ExecutionRequest.class));
    }

    @Test
    void shouldReplaceGeneralReplyContainingInternalIdentifier() {
        ToolRegistry registry = new ToolRegistry(List.of(AgentToolDefinitions.rankMoviePlan()));
        PlanSchemaValidator validator = new PlanSchemaValidator(registry);
        ExecutionPlanStateMachine stateMachine = new ExecutionPlanStateMachine(registry);
        ModelGateway gateway = movieGateway();
        RankMoviePlanExecutionAdapter adapter = Mockito.mock(RankMoviePlanExecutionAdapter.class);
        when(adapter.targetName()).thenReturn(RankMoviePlanTool.TARGET_NAME);
        when(adapter.definition()).thenReturn(AgentToolDefinitions.rankMoviePlan());
        when(gateway.classifyIntent(any())).thenReturn(AgentIntent.GENERAL_CHAT);
        when(gateway.generateReply(any())).thenReturn(new ReplyGenerationResponse(
                "请提供 cinemaId=1001。", AgentReplyMessageType.TEXT, new TextReplyFacts()));
        MultiToolSupervisor supervisor = new MultiToolSupervisor(gateway, registry, validator, stateMachine,
                List.of(adapter));

        var result = supervisor.run(new MultiToolSupervisorRequest(
                "unsafe-text", "你好", context(), "run-unsafe", "trace-unsafe", 3_000L));

        assertThat(result.generatedReply().text()).doesNotContainIgnoringCase("cinemaId");
        Mockito.verify(gateway, never()).generatePlan(any());
    }

    @Test
    void shouldPrefetchEnabledProfileOnlyIntoPlanRequestAndGenerateServerPlanId() {
        ToolRegistry registry = new ToolRegistry(List.of(AgentToolDefinitions.rankMoviePlan()));
        PlanSchemaValidator validator = new PlanSchemaValidator(registry);
        ExecutionPlanStateMachine stateMachine = new ExecutionPlanStateMachine(registry);
        ModelGateway gateway = movieGateway();
        GetProfileSummaryTool profileTool = Mockito.mock(GetProfileSummaryTool.class);
        RankMoviePlanExecutionAdapter adapter = Mockito.mock(RankMoviePlanExecutionAdapter.class);
        when(adapter.targetName()).thenReturn(RankMoviePlanTool.TARGET_NAME);
        when(adapter.definition()).thenReturn(AgentToolDefinitions.rankMoviePlan());
        var summary = new ProfileSummary(true, 3L, Instant.parse("2026-08-05T02:00:00Z"), List.of(
                new ProfileSummary.Tag(ProfileTagType.MOVIE_GENRE, "科幻", ProfileTagPolarity.LIKE,
                        new BigDecimal("0.8"), new BigDecimal("0.9"), ProfileTagSource.CONVERSATION,
                        Instant.parse("2026-08-05T01:00:00Z"))));
        when(profileTool.execute(any())).thenReturn(new ToolResult<>(ToolStatus.SUCCESS, summary, null, false,
                false, null, false, null, 3L, Instant.now(), Instant.now().plusSeconds(1)));
        CandidatePlan plan = new CandidatePlan("model-plan", 1, List.of());
        var context = context();
        when(gateway.generatePlan(any())).thenReturn(
                new PlanGenerationResponse(plan, validator.validate(plan, context)));
        when(adapter.execute(any(ReadOnlyToolExecutionAdapter.ExecutionRequest.class)))
                .thenAnswer(invocation -> successfulExecution(stateMachine, invocation.getArgument(0)));
        MultiToolSupervisor supervisor = new MultiToolSupervisor(gateway, registry, validator, stateMachine,
                List.of(adapter), new ProfileContextPrefetcher(profileTool));

        var result = supervisor.run(new MultiToolSupervisorRequest("request-profile", "推荐", context, "run-profile",
                "trace-profile", 3_000L));

        assertThat(result.state().nodeState("rank-movie-plan").status())
                .isEqualTo(com.miaoyu.ticket.agent.domain.plan.PlanNodeStatus.SUCCESS);
        assertThat(result.state().nodeState("render-result").status())
                .isEqualTo(com.miaoyu.ticket.agent.domain.plan.PlanNodeStatus.SUCCESS);
        Mockito.verify(gateway, never()).generatePlan(any());
        Mockito.verify(profileTool, never()).execute(any());
    }

    @Test
    void shouldUseEmptyProfileContextWhenProfileReadFails() {
        GetProfileSummaryTool profileTool = Mockito.mock(GetProfileSummaryTool.class);
        when(profileTool.execute(any())).thenThrow(new IllegalStateException("internal"));
        assertThat(new ProfileContextPrefetcher(profileTool).prefetch(new MultiToolSupervisorRequest("r", "i",
                new PlanValidationContext(Map.of(), Map.of(), new SlotSnapshot(7L, Map.of())), "run", "trace", 1L)))
                .isEmpty();
    }

    @Test
    void shouldExecuteReadOnlyNodeWithoutLettingModelPlanWriteTool() {
        ToolRegistry registry = new ToolRegistry(List.of(
                AgentToolDefinitions.rankMoviePlan(), AgentToolDefinitions.createOrder()));
        PlanSchemaValidator validator = new PlanSchemaValidator(registry);
        ExecutionPlanStateMachine stateMachine = new ExecutionPlanStateMachine(registry);
        ModelGateway gateway = movieGateway();
        RankMoviePlanExecutionAdapter adapter = Mockito.mock(RankMoviePlanExecutionAdapter.class);
        when(adapter.targetName()).thenReturn(RankMoviePlanTool.TARGET_NAME);
        when(adapter.definition()).thenReturn(AgentToolDefinitions.rankMoviePlan());
        CandidatePlan plan = candidatePlan();
        when(gateway.generatePlan(any()))
                .thenReturn(new PlanGenerationResponse(plan, validator.validate(plan, context())));
        when(adapter.execute(any(ReadOnlyToolExecutionAdapter.ExecutionRequest.class)))
                .thenAnswer(invocation -> {
            var request = invocation.getArgument(0,
                    com.miaoyu.ticket.agent.application.tool.ReadOnlyToolExecutionAdapter.ExecutionRequest.class);
            var running = stateMachine.startNode(request.state(), request.nodeId());
            ToolResult<FixedRecommendationResult> result = new ToolResult<>(
                    ToolStatus.SUCCESS, null, null, false, false, "CONTINUE", false, null, 1L, null, null);
            return new com.miaoyu.ticket.agent.application.tool.ReadOnlyToolExecutionAdapter.ExecutionResult(
                    stateMachine.recordToolResult(running, request.nodeId(), result), result);
        });

        MultiToolSupervisor supervisor = new MultiToolSupervisor(
                gateway, registry, validator, stateMachine, List.of(adapter));
        var result = supervisor.run(new MultiToolSupervisorRequest(
                "request-1", "推荐并建单", context(), "run-1", "trace-1", 3_000L));

        assertThat(result.validation().isValid()).isTrue();
        assertThat(result.toolResults()).hasSize(1);
        assertThat(result.toolResults().getFirst().nodeId()).isEqualTo("rank-movie-plan");
        assertThat(result.awaitingConfirmation()).isFalse();
        assertThat(result.safeNextAction()).isNull();
        assertThat(result.state().nodeState("render-result").status())
                .isEqualTo(com.miaoyu.ticket.agent.domain.plan.PlanNodeStatus.SUCCESS);
    }

    @Test
    void shouldSkipWaitingConfirmationAndWriteNodeWhenRankMoviePlanFinallyFails() {
        ToolRegistry registry = new ToolRegistry(List.of(
                AgentToolDefinitions.rankMoviePlan(), AgentToolDefinitions.createOrder()));
        PlanSchemaValidator validator = new PlanSchemaValidator(registry);
        ExecutionPlanStateMachine stateMachine = new ExecutionPlanStateMachine(registry);
        ModelGateway gateway = movieGateway();
        RankMoviePlanExecutionAdapter adapter = Mockito.mock(RankMoviePlanExecutionAdapter.class);
        when(adapter.targetName()).thenReturn(RankMoviePlanTool.TARGET_NAME);
        when(adapter.definition()).thenReturn(AgentToolDefinitions.rankMoviePlan());
        CandidatePlan plan = candidatePlan();
        when(gateway.generatePlan(any()))
                .thenReturn(new PlanGenerationResponse(plan, validator.validate(plan, context())));
        when(adapter.execute(any(ReadOnlyToolExecutionAdapter.ExecutionRequest.class))).thenAnswer(invocation -> {
            var request = invocation.getArgument(0, ReadOnlyToolExecutionAdapter.ExecutionRequest.class);
            var running = stateMachine.startNode(request.state(), request.nodeId());
            ToolResult<FixedRecommendationResult> failure = new ToolResult<>(
                    ToolStatus.FAILED, null, 306002, false, false, "CHECK_INPUT", false, null, 1L, null, null);
            return new ReadOnlyToolExecutionAdapter.ExecutionResult(
                    stateMachine.recordToolResult(running, request.nodeId(), failure), failure);
        });

        MultiToolSupervisor supervisor = new MultiToolSupervisor(
                gateway, registry, validator, stateMachine, List.of(adapter));
        var result = supervisor.run(new MultiToolSupervisorRequest(
                "request-failed", "推荐并建单", context(), "run-failed", "trace-failed", 3_000L));

        assertThat(result.state().nodeState("rank-movie-plan").status())
                .isEqualTo(com.miaoyu.ticket.agent.domain.plan.PlanNodeStatus.FAILED);
        assertThat(result.state().nodeState("render-result").status())
                .isEqualTo(com.miaoyu.ticket.agent.domain.plan.PlanNodeStatus.SKIPPED);
        assertThat(result.state().nodeState("render-result").skipReason()).isEqualTo("UPSTREAM_FAILED");
    }

    @Test
    void shouldRaisePlanVersionAndPreserveCompletedReadOnlyNodeWhenReplanning() {
        ToolRegistry registry = new ToolRegistry(List.of(
                AgentToolDefinitions.rankMoviePlan(), AgentToolDefinitions.createOrder()));
        PlanSchemaValidator validator = new PlanSchemaValidator(registry);
        ExecutionPlanStateMachine stateMachine = new ExecutionPlanStateMachine(registry);
        ModelGateway gateway = movieGateway();
        RankMoviePlanExecutionAdapter adapter = Mockito.mock(RankMoviePlanExecutionAdapter.class);
        when(adapter.targetName()).thenReturn(RankMoviePlanTool.TARGET_NAME);
        when(adapter.definition()).thenReturn(AgentToolDefinitions.rankMoviePlan());
        CandidatePlan firstPlan = candidatePlan();
        CandidatePlan replacement = new CandidatePlan("plan-2", 2, List.of(
                new CandidatePlanNode("confirm-2", PlanNodeType.CONFIRM_ACTION, null, List.of(), List.of(),
                        FailurePolicy.FAIL)));
        when(gateway.generatePlan(any()))
                .thenReturn(new PlanGenerationResponse(replacement, validator.validate(replacement, context())));
        when(adapter.execute(any(ReadOnlyToolExecutionAdapter.ExecutionRequest.class)))
                .thenAnswer(invocation -> {
            var request = invocation.getArgument(0,
                    com.miaoyu.ticket.agent.application.tool.ReadOnlyToolExecutionAdapter.ExecutionRequest.class);
            var running = stateMachine.startNode(request.state(), request.nodeId());
            ToolResult<FixedRecommendationResult> success = new ToolResult<>(
                    ToolStatus.SUCCESS, null, null, false, false, "CONTINUE", false, null, 1L, null, null);
            return new com.miaoyu.ticket.agent.application.tool.ReadOnlyToolExecutionAdapter.ExecutionResult(
                    stateMachine.recordToolResult(running, request.nodeId(), success), success);
        });
        MultiToolSupervisor supervisor = new MultiToolSupervisor(
                gateway, registry, validator, stateMachine, List.of(adapter));
        MultiToolSupervisorRequest request = new MultiToolSupervisorRequest(
                "request-3", "推荐并建单", context(), "run-3", "trace-3", 3_000L);

        var replanned = supervisor.replan(request, supervisor.run(request));

        assertThat(replanned.state().plan().version()).isEqualTo(2);
        assertThat(replanned.state().nodeState("confirm-2").status())
                .isEqualTo(com.miaoyu.ticket.agent.domain.plan.PlanNodeStatus.WAITING_CONFIRMATION);
    }

    @Test
    void shouldRejectUnknownToolBeforeAdapterExecution() {
        ToolRegistry registry = new ToolRegistry(List.of(AgentToolDefinitions.rankMoviePlan()));
        PlanSchemaValidator validator = new PlanSchemaValidator(registry);
        ExecutionPlanStateMachine stateMachine = new ExecutionPlanStateMachine(registry);
        ModelGateway gateway = movieGateway();
        RankMoviePlanExecutionAdapter adapter = Mockito.mock(RankMoviePlanExecutionAdapter.class);
        when(adapter.targetName()).thenReturn(RankMoviePlanTool.TARGET_NAME);
        when(adapter.definition()).thenReturn(AgentToolDefinitions.rankMoviePlan());
        CandidatePlan unknown = new CandidatePlan("unknown", 1, List.of(new CandidatePlanNode(
                "unknown", PlanNodeType.CALL_TOOL, "notRegistered", List.of(), List.of(), FailurePolicy.FAIL)));
        when(gateway.generatePlan(any()))
                .thenReturn(new PlanGenerationResponse(unknown, validator.validate(unknown, context())));
        when(adapter.execute(any(ReadOnlyToolExecutionAdapter.ExecutionRequest.class)))
                .thenAnswer(invocation -> successfulExecution(stateMachine, invocation.getArgument(0)));

        MultiToolSupervisor supervisor = new MultiToolSupervisor(
                gateway, registry, validator, stateMachine, List.of(adapter));
        var result = supervisor.run(new MultiToolSupervisorRequest(
                "request-2", "测试", context(), "run-2", "trace-2", 3_000L));

        assertThat(result.validation().isValid()).isTrue();
        assertThat(result.safeNextAction()).isNull();
        Mockito.verify(adapter, Mockito.times(1))
                .execute(any(ReadOnlyToolExecutionAdapter.ExecutionRequest.class));
    }

    @Test
    void shouldExecuteEachIndependentReadOnlyNodeOnlyOnce() {
        ToolRegistry registry = new ToolRegistry(List.of(AgentToolDefinitions.rankMoviePlan()));
        PlanSchemaValidator validator = new PlanSchemaValidator(registry);
        ExecutionPlanStateMachine stateMachine = new ExecutionPlanStateMachine(registry);
        ModelGateway gateway = movieGateway();
        RankMoviePlanExecutionAdapter adapter = Mockito.mock(RankMoviePlanExecutionAdapter.class);
        when(adapter.targetName()).thenReturn(RankMoviePlanTool.TARGET_NAME);
        when(adapter.definition()).thenReturn(AgentToolDefinitions.rankMoviePlan());
        CandidatePlan plan = new CandidatePlan("two-read", 1, List.of(
                rankNode("rank-a"), rankNode("rank-b")));
        when(gateway.generatePlan(any()))
                .thenReturn(new PlanGenerationResponse(plan, validator.validate(plan, context())));
        when(adapter.execute(any(ReadOnlyToolExecutionAdapter.ExecutionRequest.class)))
                .thenAnswer(invocation -> {
                    var request = invocation.getArgument(0,
                            ReadOnlyToolExecutionAdapter.ExecutionRequest.class);
                    var running = stateMachine.startNode(request.state(), request.nodeId());
                    ToolResult<FixedRecommendationResult> success = new ToolResult<>(
                            ToolStatus.SUCCESS, null, null, false, false, "CONTINUE", false, null, 1L, null, null);
                    return new com.miaoyu.ticket.agent.application.tool.ReadOnlyToolExecutionAdapter.ExecutionResult(
                            stateMachine.recordToolResult(running, request.nodeId(), success), success);
                });
        MultiToolSupervisor supervisor = new MultiToolSupervisor(
                gateway, registry, validator, stateMachine, List.of(adapter));

        var result = supervisor.run(new MultiToolSupervisorRequest(
                "request-4", "双推荐", context(), "run-4", "trace-4", 3_000L));

        assertThat(result.toolResults()).extracting(MultiToolSupervisorResult.NodeToolResult::nodeId)
                .containsExactly("rank-movie-plan");
        Mockito.verify(adapter, Mockito.times(1))
                .execute(any(ReadOnlyToolExecutionAdapter.ExecutionRequest.class));
    }

    @Test
    void shouldReplanAndContinueOnlyAfterReadOnlyToolSuggestsIt() {
        ToolRegistry registry = new ToolRegistry(List.of(AgentToolDefinitions.rankMoviePlan()));
        PlanSchemaValidator validator = new PlanSchemaValidator(registry);
        ExecutionPlanStateMachine stateMachine = new ExecutionPlanStateMachine(registry);
        ModelGateway gateway = movieGateway();
        RankMoviePlanExecutionAdapter adapter = Mockito.mock(RankMoviePlanExecutionAdapter.class);
        when(adapter.targetName()).thenReturn(RankMoviePlanTool.TARGET_NAME);
        when(adapter.definition()).thenReturn(AgentToolDefinitions.rankMoviePlan());
        CandidatePlan first = new CandidatePlan("first", 1, List.of(rankNode("rank-first")));
        CandidatePlan second = new CandidatePlan("second", 2, List.of(rankNode("rank-second")));
        when(gateway.generatePlan(any()))
                .thenReturn(new PlanGenerationResponse(second, validator.validate(second, context())));
        when(adapter.execute(any(ReadOnlyToolExecutionAdapter.ExecutionRequest.class))).thenAnswer(invocation -> {
            var request = invocation.getArgument(0, ReadOnlyToolExecutionAdapter.ExecutionRequest.class);
            var running = stateMachine.startNode(request.state(), request.nodeId());
            boolean firstNode = "rank-movie-plan".equals(request.nodeId());
            ToolResult<FixedRecommendationResult> result = new ToolResult<>(
                    firstNode ? ToolStatus.FAILED : ToolStatus.SUCCESS,
                    null,
                    firstNode ? 306002 : null,
                    false,
                    firstNode,
                    firstNode ? "REPLAN" : "CONTINUE",
                    false,
                    null,
                    1L,
                    null,
                    null);
            return new ReadOnlyToolExecutionAdapter.ExecutionResult(
                    stateMachine.recordToolResult(running, request.nodeId(), result), result);
        });
        MultiToolSupervisor supervisor = new MultiToolSupervisor(
                gateway, registry, validator, stateMachine, List.of(adapter));

        var result = supervisor.run(new MultiToolSupervisorRequest(
                "request-replan", "换一个方案", context(), "run-replan", "trace-replan", 3_000L));

        assertThat(result.state().plan().version()).isEqualTo(2);
        assertThat(result.toolResults()).extracting(MultiToolSupervisorResult.NodeToolResult::nodeId)
                .containsExactly("rank-movie-plan", "rank-second");
        Mockito.verify(gateway, Mockito.times(1)).generatePlan(any());
    }

    @Test
    void shouldReturnServerDerivedQuestionWhenRequiredSlotIsMissing() {
        ToolRegistry registry = new ToolRegistry(List.of(AgentToolDefinitions.rankMoviePlan()));
        PlanSchemaValidator validator = new PlanSchemaValidator(registry);
        ExecutionPlanStateMachine stateMachine = new ExecutionPlanStateMachine(registry);
        RankMoviePlanExecutionAdapter adapter = Mockito.mock(RankMoviePlanExecutionAdapter.class);
        when(adapter.targetName()).thenReturn(RankMoviePlanTool.TARGET_NAME);
        when(adapter.definition()).thenReturn(AgentToolDefinitions.rankMoviePlan());
        when(adapter.execute(any(ReadOnlyToolExecutionAdapter.ExecutionRequest.class)))
                .thenAnswer(invocation -> successfulExecution(stateMachine, invocation.getArgument(0)));
        when(adapter.execute(any(ReadOnlyToolExecutionAdapter.ExecutionRequest.class)))
                .thenAnswer(invocation -> successfulExecution(stateMachine, invocation.getArgument(0)));
        MultiToolSupervisor supervisor = new MultiToolSupervisor(
                new com.miaoyu.ticket.agent.infrastructure.model.MockModelGateway(validator, registry),
                registry, validator, stateMachine, List.of(adapter));

        var result = supervisor.run(new MultiToolSupervisorRequest(
                "request-question", "找电影", new PlanValidationContext(
                        Map.of(), Map.of(), new SlotSnapshot(1L, Map.of())),
                "run-question", "trace-question", 3_000L));

        assertThat(result.safeNextAction()).isEqualTo("QUESTION:cityCode");
        assertThat(result.state().nodeState("ask-cityCode").status())
                .isEqualTo(com.miaoyu.ticket.agent.domain.plan.PlanNodeStatus.SUCCESS);
        Mockito.verify(adapter, Mockito.never())
                .execute(any(ReadOnlyToolExecutionAdapter.ExecutionRequest.class));
    }

    @Test
    void shouldInheritMovieIntentAndCarryOriginalRequestOutsideTrustedSlots() {
        ToolRegistry registry = new ToolRegistry(List.of(AgentToolDefinitions.rankMoviePlan()));
        PlanSchemaValidator validator = new PlanSchemaValidator(registry);
        ExecutionPlanStateMachine stateMachine = new ExecutionPlanStateMachine(registry);
        ModelGateway gateway = Mockito.mock(ModelGateway.class);
        when(gateway.classifyIntent(any())).thenReturn(AgentIntent.GENERAL_CHAT);
        CandidatePlan emptyPlan = new CandidatePlan("model-plan", 1, List.of());
        PlanValidationContext answerContext = new PlanValidationContext(
                Map.of("cityCode", String.class, "date", LocalDate.class, "ticketCount", Integer.class),
                Map.of(), new SlotSnapshot(3L, Map.of(
                        "cityCode", "430100", "date", "2026-08-08", "ticketCount", "2")));
        when(gateway.generatePlan(any())).thenReturn(
                new PlanGenerationResponse(emptyPlan, validator.validate(emptyPlan, answerContext)));
        RankMoviePlanExecutionAdapter adapter = Mockito.mock(RankMoviePlanExecutionAdapter.class);
        when(adapter.targetName()).thenReturn(RankMoviePlanTool.TARGET_NAME);
        when(adapter.definition()).thenReturn(AgentToolDefinitions.rankMoviePlan());
        when(adapter.execute(any(ReadOnlyToolExecutionAdapter.ExecutionRequest.class)))
                .thenAnswer(invocation -> successfulExecution(stateMachine, invocation.getArgument(0)));
        MultiToolSupervisor supervisor = new MultiToolSupervisor(
                gateway, registry, validator, stateMachine, List.of(adapter));

        supervisor.run(new MultiToolSupervisorRequest(
                "follow-up", "明天", answerContext, "run-follow-up", "trace-follow-up", 3_000L,
                null, null, "我想看长沙蜘蛛侠", AgentIntent.MOVIE));

        Mockito.verify(gateway, never()).classifyIntent(any());
        Mockito.verify(gateway, never()).generatePlan(any());
        Mockito.verify(adapter).execute(any(ReadOnlyToolExecutionAdapter.ExecutionRequest.class));
    }

    @Test
    void shouldAdvanceRenderResultAfterReadOnlyToolAndLeaveCardReady() {
        ToolRegistry registry = new ToolRegistry(List.of(AgentToolDefinitions.rankMoviePlan()));
        PlanSchemaValidator validator = new PlanSchemaValidator(registry);
        ExecutionPlanStateMachine stateMachine = new ExecutionPlanStateMachine(registry);
        RankMoviePlanExecutionAdapter adapter = Mockito.mock(RankMoviePlanExecutionAdapter.class);
        when(adapter.targetName()).thenReturn(RankMoviePlanTool.TARGET_NAME);
        when(adapter.definition()).thenReturn(AgentToolDefinitions.rankMoviePlan());
        when(adapter.execute(any(ReadOnlyToolExecutionAdapter.ExecutionRequest.class))).thenAnswer(invocation -> {
            var request = invocation.getArgument(0, ReadOnlyToolExecutionAdapter.ExecutionRequest.class);
            var running = stateMachine.startNode(request.state(), request.nodeId());
            ToolResult<FixedRecommendationResult> success = new ToolResult<>(
                    ToolStatus.SUCCESS, new FixedRecommendationResult(
                            "v1", List.of(), false, List.of("SHOWTIME"), "fixture",
                            java.time.Instant.parse("2026-08-06T00:00:00Z"),
                            java.time.Instant.parse("2026-08-06T01:00:00Z"), false),
                    null, false, false, "CONTINUE", false, null, 1L, null, null);
            return new ReadOnlyToolExecutionAdapter.ExecutionResult(
                    stateMachine.recordToolResult(running, request.nodeId(), success), success);
        });
        MultiToolSupervisor supervisor = new MultiToolSupervisor(
                new com.miaoyu.ticket.agent.infrastructure.model.MockModelGateway(validator, registry),
                registry, validator, stateMachine, List.of(adapter));

        var result = supervisor.run(new MultiToolSupervisorRequest(
                "request-render", "推荐", context(), "run-render", "trace-render", 3_000L));

        assertThat(result.state().nodeState("render-result").status())
                .isEqualTo(com.miaoyu.ticket.agent.domain.plan.PlanNodeStatus.SUCCESS);
        assertThat(result.toolResults()).hasSize(1);
        assertThat(result.safeNextAction()).isNull();
    }

    private static CandidatePlan candidatePlan() {
        return new CandidatePlan("plan-1", 1, List.of(
                new CandidatePlanNode(
                        "rank", PlanNodeType.CALL_TOOL, RankMoviePlanTool.TARGET_NAME,
                        rankInputReferences(),
                        List.of(), FailurePolicy.FAIL),
                new CandidatePlanNode("confirm", PlanNodeType.CONFIRM_ACTION, null, List.of(), List.of("rank"),
                        FailurePolicy.FAIL)));
    }

    private static ModelGateway movieGateway() {
        ModelGateway gateway = Mockito.mock(ModelGateway.class);
        Mockito.doReturn(AgentIntent.MOVIE).when(gateway).classifyIntent(any());
        return gateway;
    }

    private static CandidatePlanNode rankNode(String nodeId) {
        return new CandidatePlanNode(
                nodeId, PlanNodeType.CALL_TOOL, RankMoviePlanTool.TARGET_NAME,
                rankInputReferences(),
                List.of(), FailurePolicy.FAIL);
    }

    private static ReadOnlyToolExecutionAdapter.ExecutionResult successfulExecution(
            ExecutionPlanStateMachine stateMachine, ReadOnlyToolExecutionAdapter.ExecutionRequest request) {
        var running = stateMachine.startNode(request.state(), request.nodeId());
        ToolResult<FixedRecommendationResult> success = new ToolResult<>(
                ToolStatus.SUCCESS, null, null, false, false, "CONTINUE", false, null, 1L, null, null);
        return new ReadOnlyToolExecutionAdapter.ExecutionResult(
                stateMachine.recordToolResult(running, request.nodeId(), success), success);
    }

    private static PlanValidationContext context() {
        return new PlanValidationContext(
                Map.of("cityCode", String.class, "date", LocalDate.class, "ticketCount", Integer.class,
                        "movieId", String.class, "cinemaId", String.class, "showId", String.class,
                        "seatIds", List.class),
                Map.of(),
                new SlotSnapshot(1L, Map.of(
                        "cityCode", "430100", "ticketCount", "1", "movieId", "1", "cinemaId", "2",
                        "date", "2026-08-06", "showId", "3", "seatIds", "4,5")));
    }

    private static List<InputReference> rankInputReferences() {
        return List.of(
                new InputReference("cityCode", InputReferenceSource.SLOT, "cityCode"),
                new InputReference("date", InputReferenceSource.SLOT, "date"),
                new InputReference("ticketCount", InputReferenceSource.SLOT, "ticketCount"),
                new InputReference("movieId", InputReferenceSource.SLOT, "movieId"),
                new InputReference("cinemaId", InputReferenceSource.SLOT, "cinemaId"));
    }
}
