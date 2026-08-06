package com.miaoyu.ticket.recommendation.application;

import com.miaoyu.ticket.recommendation.domain.RecommendationPlanResult;
import io.micrometer.core.instrument.MeterRegistry;
import java.util.Locale;
import java.util.Set;
import java.util.TreeSet;
import org.springframework.stereotype.Component;

/** 记录推荐结果的最小运行指标，不保存任何用户、会话或位置资料。 */
@Component
public class RecommendationMetricsRecorder {

    /*
     * 指标只统计一次推荐计算的结果，供空方案率和降级率使用。
     * 标签只能来自固定算法版本、已知候选来源和缺失因素，不能放入 userId、runId、traceId 或坐标。
     * 缺失因素排序后再写标签，避免同一组因素因列表顺序不同而拆成多个时间序列。
     * 空方案是成功业务结果，不应被误记为调用失败。
     * 降级率以所有推荐结果为分母，而不是只以失败调用为分母。
     */
    private static final String TOTAL_METRIC = "recommendation.result.total";
    /** 分母：每次得到最终推荐结果时加一。 */
    private static final String EMPTY_PLAN_METRIC = "recommendation.result.empty";
    /** 分子：硬过滤后没有任何可展示方案时加一。 */
    private static final String DEGRADED_METRIC = "recommendation.result.degraded";
    /** 分子：结果明确说明使用降级数据或策略时加一。 */
    private static final Set<String> ALLOWED_MISSING_FACTORS = Set.of(
            "RATING", "TIME", "SHOWTIME", "CONTENT", "DISTANCE");

    private final MeterRegistry meterRegistry;

    /** 注入应用统一监控注册器，测试可替换为内存注册器。 */
    public RecommendationMetricsRecorder(MeterRegistry meterRegistry) {
        // Spring Boot 的 Actuator 已提供 MeterRegistry；这里不另建全局静态注册表。
        this.meterRegistry = meterRegistry;
    }

    /**
     * 在推荐应用服务拿到最终结果后调用一次。
     *
     * <p>三项 Counter 使用相同标签，因此看板可以用 empty 或 degraded 除以 total 得到对应比率；不会记录
     * 输入条件、影片 ID、影院 ID 或场次 ID。</p>
     */
    public void record(RecommendationPlanResult result) {
        String algorithmVersion = safeAlgorithmVersion(result.algorithmVersion());
        String candidateSource = safeCandidateSource(result.source());
        String missingFactor = safeMissingFactors(result.missingFactors());
        // 三个指标使用同一组标签，查询时才可以直接相除计算比例。
        String[] tags = new String[] {
                "algorithm_version", algorithmVersion,
                "candidate_source", candidateSource,
                "missing_factor", missingFactor
        };
        meterRegistry.counter(TOTAL_METRIC, tags).increment();
        if (result.plans().isEmpty()) {
            // 空方案代表条件不满足，不代表推荐工具调用失败。
            meterRegistry.counter(EMPTY_PLAN_METRIC, tags).increment();
        }
        if (result.degraded()) {
            // 仅依据领域结果的明确标记统计，不能从错误文本猜测是否降级。
            meterRegistry.counter(DEGRADED_METRIC, tags).increment();
        }
    }

    private static String safeAlgorithmVersion(String algorithmVersion) {
        // 版本来自 D 固定算法配置；非法或缺失值归入 unknown，避免将异常文本变成高基数标签。
        return algorithmVersion != null && algorithmVersion.matches("rec-[a-z0-9-]{1,40}")
                ? algorithmVersion : "unknown";
    }

    /** 仅保留来源分类，Provider 名称和请求关联信息不能作为标签。 */
    private static String safeCandidateSource(String source) {
        // 来源只保留固定前缀，丢弃可能包含 Provider 明细的后半段。
        if (source == null || source.isBlank()) {
            return "unknown";
        }
        String normalized = source.toUpperCase(Locale.ROOT);
        int separator = normalized.indexOf(':');
        String prefix = separator < 0 ? normalized : normalized.substring(0, separator);
        return switch (prefix) {
            case "TICKETING", "CONTENT", "MOCK", "SNAPSHOT", "CACHE" -> prefix.toLowerCase(Locale.ROOT);
            default -> "unknown";
        };
    }

    private static String safeMissingFactors(java.util.List<String> missingFactors) {
        TreeSet<String> normalized = new TreeSet<>();
        for (String factor : missingFactors) {
            // 未列入协议的文本直接忽略，防止异常消息或调用方内容进入监控标签。
            if (factor != null && ALLOWED_MISSING_FACTORS.contains(factor.toUpperCase(Locale.ROOT))) {
                normalized.add(factor.toUpperCase(Locale.ROOT));
            }
        }
        return normalized.isEmpty() ? "none" : String.join("_", normalized).toLowerCase(Locale.ROOT);
    }
}
