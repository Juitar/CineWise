package com.miaoyu.ticket.recommendation.api;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.miaoyu.ticket.agent.domain.tool.ToolContext;
import com.miaoyu.ticket.agent.domain.tool.ToolStatus;
import com.miaoyu.ticket.content.domain.ContentSourceType;
import com.miaoyu.ticket.recommendation.application.FixedRecommendationCatalog;
import com.miaoyu.ticket.recommendation.application.FixedRecommendationQueryService;
import com.miaoyu.ticket.recommendation.application.PersonalizedRecommendationQueryService;
import com.miaoyu.ticket.recommendation.domain.RecommendationPlanResult;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class RankMoviePlanToolTest {

    /*
     * 工具测试不启动 Agent 路由或 SSE。
     * 重点确认 D 返回的公共结果可被 B 安全消费。
     * 无场次必须是成功降级，而不是伪造可购卡片。
     * 错误 targetName 必须在工具边界拒绝。
     * 测试使用固定时钟避免数据时效断言随运行时间变化。
     * 测试不会访问 Mapper、数据库或模型服务。
     */

    private static final Instant NOW = Instant.parse("2026-08-03T00:00:00Z");
    private static final String DISTANCE_CONTEXT_ID = "b7c4ab55-3c3a-4a45-9f0f-76d11a1585d3";

    @Test
    void shouldReturnReadOnlyToolResultWithoutPlanCardWhenShowtimeIsUnavailable() {
        RankMoviePlanTool tool = new RankMoviePlanTool(new FixedRecommendationQueryService(
                () -> new FixedRecommendationCatalog(
                        "fixed-rec-v1", "FIXED_RECOMMENDATION", ContentSourceType.MOCK, 360),
                query -> List.of(),
                Clock.fixed(NOW, ZoneOffset.UTC)));

        var result = tool.execute(
                new ToolContext("run-1", "node-1", "rankMoviePlan", List.of(), 3_000L, "trace-1", null, null, 2L),
                new RankMoviePlanCommand("101", "201", LocalDate.of(2026, 8, 3), null, null));

        // 无场次是正常降级结果，不能被误报为失败或伪造成可购 PLAN_CARD。
        // 工具查询成功。
        assertThat(result.status()).isEqualTo(ToolStatus.SUCCESS);
        // 未取得 A 场次时不可购。
        assertThat(result.data().purchaseEligible()).isFalse();
        // 降级标识必须由 D 明确给出。
        assertThat(result.degraded()).isTrue();
        // 降级原因只描述场次缺失。
        assertThat(result.fallbackType()).isEqualTo("SHOWTIME_UNAVAILABLE");
        // 公共时间字段来自固定业务时钟。
        assertThat(result.dataAt()).isEqualTo(NOW);
    }

    @Test
    void shouldRejectWrongToolTargetWithoutExecutingRecommendationQuery() {
        RankMoviePlanTool tool = new RankMoviePlanTool(new FixedRecommendationQueryService(
                () -> new FixedRecommendationCatalog(
                        "fixed-rec-v1", "FIXED_RECOMMENDATION", ContentSourceType.MOCK, 360),
                query -> List.of(),
                Clock.fixed(NOW, ZoneOffset.UTC)));

        var result = tool.execute(
                new ToolContext("run-1", "node-1", "otherTool", List.of(), 3_000L, "trace-1", null, null, null),
                new RankMoviePlanCommand("101", "201", LocalDate.of(2026, 8, 3), null, null));

        // 路由名称错误时不能继续查询，防止错误计划节点调用推荐工具。
        assertThat(result.status()).isEqualTo(ToolStatus.FAILED);
        // 使用稳定的参数错误码，供 B 安全处理。
        assertThat(result.errorCode()).isEqualTo(100001);
    }

    /** 生产容器必须选择双参数构造器，避免新版入口退回旧固定查询。 */
    @Test
    void shouldInjectPersonalizedServiceIntoProductionToolBean() {
        FixedRecommendationQueryService fixed = mock(FixedRecommendationQueryService.class);
        PersonalizedRecommendationQueryService personalized = mock(PersonalizedRecommendationQueryService.class);
        RecommendationPlanResult expected = new RecommendationPlanResult("1.0", "rec-mvp-1", List.of(),
                List.of("SHOWTIME"), null, false, "TICKETING:MOCK", NOW, NOW.plusSeconds(60), true);
        when(personalized.query(any())).thenReturn(expected);
        try (AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext()) {
            context.registerBean(FixedRecommendationQueryService.class, () -> fixed);
            context.registerBean(PersonalizedRecommendationQueryService.class, () -> personalized);
            context.register(RankMoviePlanTool.class);
            context.refresh();

            var result = context.getBean(RankMoviePlanTool.class).executeRecommendationPlan(context(),
                    new RankMoviePlanCommand("430100", LocalDate.of(2026, 8, 3), 1, null, null, List.of(),
                            null, null, null, null, List.of()));

            assertThat(result.data()).isSameAs(expected);
            assertThat(result.status()).isEqualTo(ToolStatus.SUCCESS);
            assertThat(result.dataAt()).isEqualTo(NOW);
            assertThat(result.expiresAt()).isEqualTo(NOW.plusSeconds(60));
            verify(personalized).query(any());
        }
    }

    /** 完整入口缺少城市时必须拒绝，不能静默退回旧固定推荐而让调用方误以为已经得到完整方案。 */
    @Test
    void shouldRejectLegacyCommandAtCompleteRecommendationEntry() {
        PersonalizedRecommendationQueryService personalized = mock(PersonalizedRecommendationQueryService.class);
        RankMoviePlanTool tool = new RankMoviePlanTool(mock(FixedRecommendationQueryService.class), personalized);

        var result = tool.executeRecommendationPlan(context(), command());

        assertThat(result.status()).isEqualTo(ToolStatus.FAILED);
        assertThat(result.errorCode()).isEqualTo(100001);
        assertThat(result.suggestedNextAction()).isEqualTo("COMPLETE_CONSTRAINTS_REQUIRED");
        verify(personalized, org.mockito.Mockito.never()).query(any());
    }

    /** 成功夹具：可购事实完整，展示名称等没有可靠来源时按规则留空。 */
    @Test
    void recommendationPlanFixtureSuccess() {
        var tool = completeTool(successResult());

        var result = tool.executeRecommendationPlan(context(), completeCommand());

        assertThat(result.status()).isEqualTo(ToolStatus.SUCCESS);
        assertThat(result.data().plans()).hasSize(1);
        assertThat(result.data().plans().getFirst().movieName()).isEqualTo("示例影片");
        assertThat(result.data().plans().getFirst().cinemaName()).isEqualTo("示例影院");
        assertThat(result.degraded()).isFalse();
        assertFixture("recommendation-plan-success.json", false, 1);
    }

    /** 空结果夹具：查询成功但没有可购方案，不生成虚构卡片内容。 */
    @Test
    void recommendationPlanFixtureEmpty() {
        var tool = completeTool(emptyResult(false));

        var result = tool.executeRecommendationPlan(context(), completeCommand());

        assertThat(result.status()).isEqualTo(ToolStatus.SUCCESS);
        assertThat(result.data().plans()).isEmpty();
        assertThat(result.data().missingFactors()).containsExactly("SHOWTIME");
        assertThat(result.data().degraded()).isTrue();
        assertFixture("recommendation-plan-empty.json", true, 0);
    }

    /** 降级夹具：保留真实来源和时效，缺失的距离/路线字段不得被补造。 */
    @Test
    void recommendationPlanFixtureDegraded() {
        var tool = completeTool(emptyResult(true));

        var result = tool.executeRecommendationPlan(context(), completeCommand());

        assertThat(result.status()).isEqualTo(ToolStatus.SUCCESS);
        assertThat(result.data().plans()).isEmpty();
        assertThat(result.data().degraded()).isTrue();
        assertThat(result.data().source()).isEqualTo("TICKETING:MOCK");
        assertFixture("recommendation-plan-degraded.json", true, 0);
    }

    @Test
    void shouldPassOnlyDistanceContextIdAndTrustedRunIdForNearestRecommendation() {
        PersonalizedRecommendationQueryService personalized = mock(PersonalizedRecommendationQueryService.class);
        RecommendationPlanResult expected = emptyResult(false);
        when(personalized.queryWithDistanceContext(any(), org.mockito.ArgumentMatchers.eq(DISTANCE_CONTEXT_ID),
                org.mockito.ArgumentMatchers.eq("run-1"))).thenReturn(expected);
        RankMoviePlanTool tool = new RankMoviePlanTool(mock(FixedRecommendationQueryService.class), personalized);

        var result = tool.executeRecommendationPlan(distanceContext(), completeCommand());

        assertThat(result.data()).isSameAs(expected);
        verify(personalized).queryWithDistanceContext(any(), org.mockito.ArgumentMatchers.eq(DISTANCE_CONTEXT_ID),
                org.mockito.ArgumentMatchers.eq("run-1"));
    }

    private static RankMoviePlanTool completeTool(RecommendationPlanResult result) {
        PersonalizedRecommendationQueryService personalized = mock(PersonalizedRecommendationQueryService.class);
        when(personalized.query(any())).thenReturn(result);
        return new RankMoviePlanTool(mock(FixedRecommendationQueryService.class), personalized);
    }

    private static ToolContext context() {
        return new ToolContext("run-1", "node-1", RankMoviePlanTool.TARGET_NAME, List.of(), 3_000L,
                "trace-1", null, null, 2L);
    }

    private static ToolContext distanceContext() {
        return new ToolContext("run-1", "node-1", RankMoviePlanTool.TARGET_NAME, List.of(), 3_000L,
                "trace-1", null, null, 2L, DISTANCE_CONTEXT_ID, "NEAREST");
    }

    private static RankMoviePlanCommand command() {
        return new RankMoviePlanCommand("101", "201", LocalDate.of(2026, 8, 3), null, null);
    }

    private static RankMoviePlanCommand completeCommand() {
        return new RankMoviePlanCommand("430100", LocalDate.of(2026, 8, 3), 1, "101", "201", List.of(),
                null, null, null, null, List.of());
    }

    private static RecommendationPlanResult successResult() {
        var plan = new com.miaoyu.ticket.recommendation.domain.RecommendationPlan(
                com.miaoyu.ticket.recommendation.domain.RecommendationPlan.PlanType.COMPREHENSIVE,
                "101", "示例影片", "201", "示例影院", "301", new java.math.BigDecimal("39.90"),
                NOW.plusSeconds(3600), new java.math.BigDecimal("8.6"), null, null, 9.2D, List.of("价格较低"),
                List.of(new com.miaoyu.ticket.recommendation.domain.RecommendationEvidence(
                        "showtime", "301", "TICKETING:MOCK", NOW, NOW.plusSeconds(7200))),
                "TICKETING:MOCK", NOW, NOW.plusSeconds(7200), true);
        return new RecommendationPlanResult("1.0", "rec-mvp-1", List.of(plan), List.of(), null,
                false, "TICKETING:MOCK", NOW, NOW.plusSeconds(7200), false);
    }

    private static RecommendationPlanResult emptyResult(boolean withDistanceFactor) {
        return new RecommendationPlanResult("1.0", "rec-mvp-1", List.of(),
                withDistanceFactor ? List.of("SHOWTIME", "DISTANCE") : List.of("SHOWTIME"), null,
                false, "TICKETING:MOCK", NOW, NOW.plusSeconds(60), true);
    }

    private static void assertFixture(String name, boolean degraded, int planCount) {
        try (var input = new ClassPathResource("fixtures/recommendation/" + name).getInputStream()) {
            JsonNode fixture = new ObjectMapper().readTree(input);
            assertThat(fixture.path("schemaVersion").asText()).isEqualTo("1.0");
            assertThat(fixture.path("algorithmVersion").asText()).isEqualTo("rec-mvp-1");
            assertThat(fixture.path("plans")).hasSize(planCount);
            assertThat(fixture.path("degraded").asBoolean()).isEqualTo(degraded);
            assertThat(fixture.path("dataAt").asText()).isEqualTo(NOW.toString());
            assertThat(fixture.path("expiresAt").asText()).isNotBlank();
        } catch (java.io.IOException exception) {
            throw new IllegalStateException("推荐结果夹具无法读取", exception);
        }
    }
}
