package com.miaoyu.ticket.recommendation.api;

import static org.assertj.core.api.Assertions.assertThat;

import com.miaoyu.ticket.agent.domain.tool.ToolContext;
import com.miaoyu.ticket.agent.domain.tool.ToolStatus;
import com.miaoyu.ticket.content.domain.ContentSourceType;
import com.miaoyu.ticket.recommendation.application.FixedRecommendationCatalog;
import com.miaoyu.ticket.recommendation.application.FixedRecommendationQueryService;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import org.junit.jupiter.api.Test;

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
}
