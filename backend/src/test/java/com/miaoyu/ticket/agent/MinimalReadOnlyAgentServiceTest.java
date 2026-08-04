package com.miaoyu.ticket.agent;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.miaoyu.ticket.agent.application.model.ModelGateway;
import com.miaoyu.ticket.agent.application.model.PlanGenerationRequest;
import com.miaoyu.ticket.agent.application.model.PlanGenerationResponse;
import com.miaoyu.ticket.agent.application.model.ReplyGenerationRequest;
import com.miaoyu.ticket.agent.application.model.ReplyGenerationResponse;
import com.miaoyu.ticket.agent.application.reply.AgentReplyMessageType;
import com.miaoyu.ticket.agent.application.reply.ErrorReplyFacts;
import com.miaoyu.ticket.agent.application.reply.ProgressReplyFacts;
import com.miaoyu.ticket.agent.application.reply.QuestionReplyFacts;
import com.miaoyu.ticket.agent.application.reply.RecommendationReplyCandidate;
import com.miaoyu.ticket.agent.application.reply.RecommendationReplyFacts;
import com.miaoyu.ticket.agent.application.run.MinimalReadOnlyAgentRequest;
import com.miaoyu.ticket.agent.application.run.MinimalReadOnlyAgentResult;
import com.miaoyu.ticket.agent.application.run.MinimalReadOnlyAgentService;
import com.miaoyu.ticket.agent.application.tool.AgentToolDefinitions;
import com.miaoyu.ticket.agent.application.tool.RankMoviePlanExecutionAdapter;
import com.miaoyu.ticket.agent.domain.plan.CandidatePlan;
import com.miaoyu.ticket.agent.domain.plan.CandidatePlanNode;
import com.miaoyu.ticket.agent.domain.plan.FailurePolicy;
import com.miaoyu.ticket.agent.domain.plan.InputReference;
import com.miaoyu.ticket.agent.domain.plan.InputReferenceSource;
import com.miaoyu.ticket.agent.domain.plan.PlanNodeStatus;
import com.miaoyu.ticket.agent.domain.plan.PlanNodeType;
import com.miaoyu.ticket.agent.domain.plan.PlanSchemaValidator;
import com.miaoyu.ticket.agent.domain.plan.PlanValidationContext;
import com.miaoyu.ticket.agent.domain.plan.SlotSnapshot;
import com.miaoyu.ticket.agent.domain.run.ExecutionPlanStateMachine;
import com.miaoyu.ticket.agent.domain.tool.ToolContext;
import com.miaoyu.ticket.agent.domain.tool.ToolRegistry;
import com.miaoyu.ticket.agent.domain.tool.ToolResult;
import com.miaoyu.ticket.agent.domain.tool.ToolStatus;
import com.miaoyu.ticket.content.domain.ContentSourceType;
import com.miaoyu.ticket.recommendation.api.RankMoviePlanCommand;
import com.miaoyu.ticket.recommendation.api.RankMoviePlanTool;
import com.miaoyu.ticket.recommendation.application.FixedRecommendationCatalog;
import com.miaoyu.ticket.recommendation.application.FixedRecommendationQueryService;
import com.miaoyu.ticket.recommendation.application.FixedRecommendationResult;
import com.miaoyu.ticket.recommendation.application.RecommendationCandidate;
import com.miaoyu.ticket.recommendation.domain.PurchaseCandidateValidator.PurchaseCandidate;
import com.miaoyu.ticket.agent.infrastructure.model.MockModelGateway;
import java.lang.reflect.RecordComponent;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneOffset;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;

/** 从当前请求到结构化回复的最小主控测试，不启动 Controller、SSE 或持久化。 */
class MinimalReadOnlyAgentServiceTest {
    private static final Instant NOW = Instant.parse("2026-08-04T00:00:00Z");

    @Test
    void shouldKeepPlanAndReplyInputsImmutableAndTyped() {
        PlanGenerationRequest planRequest = new PlanGenerationRequest(
                "request-1",
                "推荐电影",
                Map.of("movieId", "101"),
                Set.of(RankMoviePlanTool.TARGET_NAME));
        assertThatThrownBy(() -> planRequest.confirmedSlots().put("cinemaId", "201"))
                .isInstanceOf(UnsupportedOperationException.class);

        RecommendationReplyFacts facts = recommendationFacts(false, true);
        ReplyGenerationRequest replyRequest = new ReplyGenerationRequest(
                "request-1",
                "推荐电影",
                AgentReplyMessageType.MOVIE_CARD,
                facts);
        assertThatThrownBy(() -> facts.missingFactors().add("OTHER"))
                .isInstanceOf(UnsupportedOperationException.class);
        assertThat(recordFields(RecommendationReplyFacts.class))
                .doesNotContain("userId", "rawResponse", "modelContext", "payloadJson");
        assertThat(recordFields(RecommendationReplyCandidate.class))
                .doesNotContain("seatIds", "inventory", "userId");
        assertThat(replyRequest.payload()).isSameAs(facts);
        assertThatThrownBy(() -> new PlanGenerationRequest(
                        "",
                        "推荐电影",
                        Map.of(),
                        Set.of()))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new PlanGenerationRequest(
                        "request-1",
                        " ",
                        Map.of(),
                        Set.of()))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new ReplyGenerationRequest(
                        "request-1",
                        "推荐电影",
                        AgentReplyMessageType.ERROR,
                        facts))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new MinimalReadOnlyAgentRequest(
                        "request-1",
                        "推荐电影",
                        completeContext(),
                        "run-1",
                        "trace-1",
                        0L))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void shouldGenerateDeterministicRankAndRenderPlanWithOptionalTimePair() {
        ToolRegistry registry = registry();
        MockModelGateway gateway = mockGateway(registry);
        Map<String, String> slots = Map.of(
                "movieId", "101",
                "cinemaId", "201",
                "date", "2026-08-04",
                "timeFrom", "18:00",
                "timeTo", "20:00");
        PlanGenerationRequest request = new PlanGenerationRequest(
                "request-1", "今晚看电影", slots, Set.of(RankMoviePlanTool.TARGET_NAME));

        PlanGenerationResponse first = gateway.generatePlan(request);
        PlanGenerationResponse second = gateway.generatePlan(request);

        assertThat(first.candidatePlan()).isEqualTo(second.candidatePlan());
        assertThat(second.validationResult().isValid()).isTrue();
        assertThat(first.validationResult().isValid()).isTrue();
        assertThat(first.candidatePlan().nodes()).extracting("nodeId")
                .containsExactly("rank-movie-plan", "render-result");
        assertThat(first.candidatePlan().nodes().getFirst().inputRefs()).extracting("inputName")
                .containsExactly("movieId", "cinemaId", "date", "timeFrom", "timeTo");

        PlanGenerationResponse unpairedTime = gateway.generatePlan(new PlanGenerationRequest(
                "request-1",
                "今晚看电影",
                Map.of(
                        "movieId", "101",
                        "cinemaId", "201",
                        "date", "2026-08-04",
                        "timeFrom", "18:00"),
                Set.of(RankMoviePlanTool.TARGET_NAME)));
        assertThat(unpairedTime.candidatePlan().nodes().getFirst().inputRefs()).extracting("inputName")
                .containsExactly("movieId", "cinemaId", "date");

        PlanGenerationResponse disallowed = gateway.generatePlan(new PlanGenerationRequest(
                "request-1", "今晚看电影", slots, Set.of()));
        assertThat(disallowed.candidatePlan().nodes()).isEmpty();
    }

    @Test
    void shouldAskOnlyForFirstMissingRequiredSlotWithoutCallingD() {
        RankMoviePlanTool tool = mock(RankMoviePlanTool.class);
        MinimalReadOnlyAgentResult result = service(tool).run(request(Map.of(
                "movieId", "101", "cinemaId", "201")));

        assertThat(result.reply().messageType()).isEqualTo(AgentReplyMessageType.QUESTION);
        assertThat(result.reply().payload()).isEqualTo(new QuestionReplyFacts("date"));
        assertThat(result.candidatePlan().nodes()).singleElement().satisfies(node -> {
            assertThat(node.type()).isEqualTo(PlanNodeType.ASK_USER);
            assertThat(result.state().nodeState(node.nodeId()).status()).isEqualTo(PlanNodeStatus.SUCCESS);
        });
        assertThat(result.toolResults()).isEmpty();
        verify(tool, never()).execute(any(), any());
    }

    @Test
    void shouldCallRealDToolAndReturnPurchasePlanCard() {
        PurchaseCandidate candidate = new PurchaseCandidate(
                "301",
                "101",
                "201",
                "45.00",
                NOW.plusSeconds(3_600),
                NOW.plusSeconds(1_800),
                "TICKETING:MOCK");
        RankMoviePlanTool tool = realTool(List.of(candidate));

        MinimalReadOnlyAgentResult result = service(tool).run(request(completeSlots()));

        assertThat(result.validationResult().isValid()).isTrue();
        assertThat(result.toolResults()).singleElement().satisfies(toolResult -> {
            assertThat(toolResult.status()).isEqualTo(ToolStatus.SUCCESS);
            assertThat(toolResult.data().purchaseEligible()).isTrue();
        });
        assertThat(result.reply().messageType()).isEqualTo(AgentReplyMessageType.PLAN_CARD);
        assertThat(result.reply().payload()).isInstanceOf(RecommendationReplyFacts.class);
        RecommendationReplyFacts facts = (RecommendationReplyFacts) result.reply().payload();
        assertThat(facts.candidates()).singleElement().satisfies(replyCandidate -> {
            assertThat(replyCandidate.showId()).isEqualTo("301");
            assertThat(replyCandidate.price()).isEqualTo("45.00");
            assertThat(replyCandidate.startTime()).isEqualTo(NOW.plusSeconds(3_600));
        });
        assertThat(result.state().nodeState("rank-movie-plan").status()).isEqualTo(PlanNodeStatus.SUCCESS);
        assertThat(result.state().nodeState("render-result").status()).isEqualTo(PlanNodeStatus.SUCCESS);
    }

    @Test
    void shouldKeepNoShowtimeAsSuccessfulMovieCardWithoutInventedPurchaseFacts() {
        MinimalReadOnlyAgentResult result = service(realTool(List.of())).run(request(completeSlots()));

        assertThat(result.reply().messageType()).isEqualTo(AgentReplyMessageType.MOVIE_CARD);
        RecommendationReplyFacts facts = (RecommendationReplyFacts) result.reply().payload();
        assertThat(facts.purchaseEligible()).isFalse();
        assertThat(facts.missingFactors()).containsExactly("SHOWTIME");
        assertThat(facts.degraded()).isTrue();
        assertThat(facts.candidates()).singleElement().satisfies(candidate -> {
            assertThat(candidate.showId()).isNull();
            assertThat(candidate.price()).isNull();
            assertThat(candidate.startTime()).isNull();
        });
        assertThat(result.state().nodeState("rank-movie-plan").status()).isEqualTo(PlanNodeStatus.SUCCESS);
    }

    @Test
    void shouldRejectInvalidModelPlanBeforeStateInitializationAndToolCall() {
        RankMoviePlanTool tool = mock(RankMoviePlanTool.class);
        ToolRegistry registry = registry();
        PlanSchemaValidator validator = new PlanSchemaValidator(registry);
        CandidatePlan invalidPlan = new CandidatePlan(
                "invalid-plan",
                1,
                List.of(new CandidatePlanNode(
                        "unknown",
                        PlanNodeType.CALL_TOOL,
                        "unknownTool",
                        List.of(),
                        List.of(),
                        FailurePolicy.FAIL)));
        ModelGateway invalidGateway = new FixedPlanGateway(
                new PlanGenerationResponse(invalidPlan, validator.validate(invalidPlan, completeContext())));

        MinimalReadOnlyAgentResult result = service(invalidGateway, tool, registry).run(request(completeSlots()));

        assertThat(result.validationResult().isValid()).isFalse();
        assertThat(result.state()).isNull();
        assertThat(result.toolResults()).isEmpty();
        assertThat(result.reply().messageType()).isEqualTo(AgentReplyMessageType.ERROR);
        verify(tool, never()).execute(any(), any());
    }

    @Test
    void shouldReturnStableParameterErrorWithoutCallingD() {
        RankMoviePlanTool tool = mock(RankMoviePlanTool.class);
        Map<String, String> invalidSlots = Map.of(
                "movieId", "101", "cinemaId", "201", "date", "not-a-date");

        MinimalReadOnlyAgentResult result = service(tool).run(request(invalidSlots));

        assertThat(result.toolResults()).singleElement().satisfies(toolResult -> {
            assertThat(toolResult.status()).isEqualTo(ToolStatus.FAILED);
            assertThat(toolResult.errorCode()).isEqualTo(100001);
            assertThat(toolResult.retryable()).isFalse();
        });
        assertThat(result.reply().messageType()).isEqualTo(AgentReplyMessageType.ERROR);
        assertThat(result.reply().payload()).isEqualTo(new ErrorReplyFacts(100001, List.of()));
        assertThat(result.state().nodeState("render-result").status()).isEqualTo(PlanNodeStatus.SKIPPED);
        verify(tool, never()).execute(any(), any());
    }

    @Test
    void shouldRetryOnceThenRenderSuccessOrReturnFinalError() {
        RankMoviePlanTool succeedsOnRetry = mock(RankMoviePlanTool.class);
        when(succeedsOnRetry.execute(any(ToolContext.class), any(RankMoviePlanCommand.class)))
                .thenReturn(retryableFailure(), successfulToolResult());

        MinimalReadOnlyAgentResult recovered = service(succeedsOnRetry).run(request(completeSlots()));

        assertThat(recovered.reply().messageType()).isEqualTo(AgentReplyMessageType.PLAN_CARD);
        assertThat(recovered.toolResults()).hasSize(2);
        assertThat(recovered.state().nodeState("rank-movie-plan").attemptCount()).isEqualTo(2);
        verify(succeedsOnRetry, times(2)).execute(any(ToolContext.class), any(RankMoviePlanCommand.class));

        RankMoviePlanTool alwaysFails = mock(RankMoviePlanTool.class);
        when(alwaysFails.execute(any(ToolContext.class), any(RankMoviePlanCommand.class)))
                .thenReturn(retryableFailure());
        MinimalReadOnlyAgentResult failed = service(alwaysFails).run(request(completeSlots()));

        assertThat(failed.reply().messageType()).isEqualTo(AgentReplyMessageType.ERROR);
        assertThat(failed.toolResults()).hasSize(2);
        assertThat(failed.state().nodeState("rank-movie-plan").status()).isEqualTo(PlanNodeStatus.FAILED);
        assertThat(failed.state().nodeState("render-result").status()).isEqualTo(PlanNodeStatus.SKIPPED);
        verify(alwaysFails, times(2)).execute(any(ToolContext.class), any(RankMoviePlanCommand.class));
    }

    @Test
    void shouldContinueIndependentBranchAfterAnotherBranchFails() {
        RankMoviePlanTool tool = mock(RankMoviePlanTool.class);
        when(tool.execute(any(ToolContext.class), any(RankMoviePlanCommand.class)))
                .thenReturn(nonRetryableFailure(), successfulToolResult());
        ToolRegistry registry = registry();
        PlanSchemaValidator validator = new PlanSchemaValidator(registry);
        CandidatePlan plan = independentBranchPlan();
        ModelGateway gateway = new FixedPlanGateway(
                new PlanGenerationResponse(plan, validator.validate(plan, completeContext())));

        MinimalReadOnlyAgentResult result = service(gateway, tool, registry).run(request(completeSlots()));

        assertThat(result.toolResults()).extracting(ToolResult::status)
                .containsExactly(ToolStatus.FAILED, ToolStatus.SUCCESS);
        assertThat(result.state().nodeState("rank-fail").status()).isEqualTo(PlanNodeStatus.FAILED);
        assertThat(result.state().nodeState("render-fail").status()).isEqualTo(PlanNodeStatus.SKIPPED);
        assertThat(result.state().nodeState("rank-ok").status()).isEqualTo(PlanNodeStatus.SUCCESS);
        assertThat(result.state().nodeState("render-ok").status()).isEqualTo(PlanNodeStatus.SUCCESS);
        assertThat(result.reply().messageType()).isEqualTo(AgentReplyMessageType.PLAN_CARD);
        verify(tool, times(2)).execute(any(ToolContext.class), any(RankMoviePlanCommand.class));
    }

    @Test
    void shouldReturnSamePlanAndReplyForSameFixedReadOnlyRequest() {
        MinimalReadOnlyAgentService service = service(realTool(List.of()));
        MinimalReadOnlyAgentRequest request = request(completeSlots());

        MinimalReadOnlyAgentResult first = service.run(request);
        MinimalReadOnlyAgentResult second = service.run(request);

        assertThat(first.candidatePlan()).isEqualTo(second.candidatePlan());
        assertThat(first.reply()).isEqualTo(second.reply());
        assertThat(first.toolResults()).isEqualTo(second.toolResults());
    }

    @Test
    void shouldStopRoundWhenToolIsProcessing() {
        RankMoviePlanTool tool = mock(RankMoviePlanTool.class);
        ToolResult<FixedRecommendationResult> processing = new ToolResult<>(
                ToolStatus.PROCESSING,
                null,
                null,
                false,
                false,
                "WAIT",
                false,
                null,
                1L,
                null,
                null);
        when(tool.execute(any(ToolContext.class), any(RankMoviePlanCommand.class))).thenReturn(processing);

        MinimalReadOnlyAgentResult result = service(tool).run(request(completeSlots()));

        assertThat(result.reply().messageType()).isEqualTo(AgentReplyMessageType.PROGRESS);
        assertThat(result.reply().payload()).isEqualTo(new ProgressReplyFacts("rank-movie-plan"));
        assertThat(result.state().nodeState("rank-movie-plan").status()).isEqualTo(PlanNodeStatus.RUNNING);
        assertThat(result.state().nodeState("render-result").status()).isEqualTo(PlanNodeStatus.PENDING);
        verify(tool).execute(any(ToolContext.class), any(RankMoviePlanCommand.class));
    }

    private static MinimalReadOnlyAgentService service(RankMoviePlanTool tool) {
        ToolRegistry registry = registry();
        return service(mockGateway(registry), tool, registry);
    }

    private static MinimalReadOnlyAgentService service(
            ModelGateway gateway, RankMoviePlanTool tool, ToolRegistry registry) {
        PlanSchemaValidator validator = new PlanSchemaValidator(registry);
        ExecutionPlanStateMachine stateMachine = new ExecutionPlanStateMachine(registry);
        return new MinimalReadOnlyAgentService(
                gateway,
                registry,
                validator,
                stateMachine,
                new RankMoviePlanExecutionAdapter(tool, stateMachine));
    }

    private static MockModelGateway mockGateway(ToolRegistry registry) {
        return new MockModelGateway(new PlanSchemaValidator(registry), registry);
    }

    private static ToolRegistry registry() {
        return new ToolRegistry(List.of(AgentToolDefinitions.rankMoviePlan()));
    }

    private static MinimalReadOnlyAgentRequest request(Map<String, String> slots) {
        Map<String, Class<?>> types = slots.keySet().stream().collect(Collectors.toMap(
                name -> name,
                name -> switch (name) {
                    case "date" -> LocalDate.class;
                    case "timeFrom", "timeTo" -> LocalTime.class;
                    default -> String.class;
                }));
        return new MinimalReadOnlyAgentRequest(
                "request-1",
                "帮我推荐电影",
                new PlanValidationContext(types, Map.of(), new SlotSnapshot(1L, slots)),
                "run-1",
                "trace-1",
                30_000L);
    }

    private static PlanValidationContext completeContext() {
        return request(completeSlots()).validationContext();
    }

    private static Map<String, String> completeSlots() {
        return Map.of("movieId", "101", "cinemaId", "201", "date", "2026-08-04");
    }

    private static RankMoviePlanTool realTool(List<PurchaseCandidate> candidates) {
        return new RankMoviePlanTool(new FixedRecommendationQueryService(
                () -> new FixedRecommendationCatalog(
                        "fixed-rec-v1", "FIXED_RECOMMENDATION", ContentSourceType.MOCK, 360L),
                query -> candidates,
                Clock.fixed(NOW, ZoneOffset.UTC)));
    }

    private static ToolResult<FixedRecommendationResult> retryableFailure() {
        return new ToolResult<>(
                ToolStatus.FAILED,
                null,
                100001,
                true,
                false,
                "RETRY",
                false,
                null,
                1L,
                null,
                null);
    }

    private static ToolResult<FixedRecommendationResult> nonRetryableFailure() {
        return new ToolResult<>(
                ToolStatus.FAILED,
                null,
                100001,
                false,
                false,
                "CHECK_INPUT",
                false,
                null,
                1L,
                null,
                null);
    }

    private static CandidatePlan independentBranchPlan() {
        List<InputReference> inputs = List.of(
                new InputReference("movieId", InputReferenceSource.SLOT, "movieId"),
                new InputReference("cinemaId", InputReferenceSource.SLOT, "cinemaId"),
                new InputReference("date", InputReferenceSource.SLOT, "date"));
        return new CandidatePlan(
                "independent-plan",
                1,
                List.of(
                        new CandidatePlanNode(
                                "rank-fail",
                                PlanNodeType.CALL_TOOL,
                                RankMoviePlanTool.TARGET_NAME,
                                inputs,
                                List.of(),
                                FailurePolicy.FAIL),
                        new CandidatePlanNode(
                                "render-fail",
                                PlanNodeType.RENDER_RESULT,
                                null,
                                List.of(),
                                List.of("rank-fail"),
                                FailurePolicy.FAIL),
                        new CandidatePlanNode(
                                "rank-ok",
                                PlanNodeType.CALL_TOOL,
                                RankMoviePlanTool.TARGET_NAME,
                                inputs,
                                List.of(),
                                FailurePolicy.FAIL),
                        new CandidatePlanNode(
                                "render-ok",
                                PlanNodeType.RENDER_RESULT,
                                null,
                                List.of(),
                                List.of("rank-ok"),
                                FailurePolicy.FAIL)));
    }

    private static ToolResult<FixedRecommendationResult> successfulToolResult() {
        FixedRecommendationResult data = new FixedRecommendationResult(
                "fixed-rec-v1",
                List.of(new RecommendationCandidate(
                        "101",
                        "201",
                        "301",
                        "45.00",
                        NOW.plusSeconds(3_600),
                        "TICKETING:MOCK",
                        false,
                        true)),
                true,
                List.of(),
                "FIXED_RECOMMENDATION",
                NOW,
                NOW.plusSeconds(1_800),
                false);
        return new ToolResult<>(
                ToolStatus.SUCCESS,
                data,
                null,
                false,
                false,
                "RENDER_RESULT",
                false,
                null,
                1L,
                data.dataAt(),
                data.expiresAt());
    }

    private static RecommendationReplyFacts recommendationFacts(boolean purchaseEligible, boolean degraded) {
        RecommendationReplyCandidate candidate = new RecommendationReplyCandidate(
                "101",
                "201",
                purchaseEligible ? "301" : null,
                purchaseEligible ? "45.00" : null,
                purchaseEligible ? NOW.plusSeconds(3_600) : null,
                "FIXED_RECOMMENDATION",
                false,
                purchaseEligible);
        return new RecommendationReplyFacts(
                "fixed-rec-v1",
                List.of(candidate),
                purchaseEligible,
                purchaseEligible ? List.of() : List.of("SHOWTIME"),
                "FIXED_RECOMMENDATION",
                NOW,
                NOW.plusSeconds(1_800),
                false,
                degraded);
    }

    private static Set<String> recordFields(Class<?> type) {
        return Arrays.stream(type.getRecordComponents())
                .map(RecordComponent::getName)
                .collect(Collectors.toSet());
    }

    private static final class FixedPlanGateway implements ModelGateway {
        private final PlanGenerationResponse response;

        private FixedPlanGateway(PlanGenerationResponse response) {
            this.response = response;
        }

        @Override
        public PlanGenerationResponse generatePlan(PlanGenerationRequest request) {
            return response;
        }

        @Override
        public ReplyGenerationResponse generateReply(ReplyGenerationRequest request) {
            return new ReplyGenerationResponse(
                    "当前请求未通过安全校验。",
                    request.requestedType(),
                    request.payload());
        }
    }
}
