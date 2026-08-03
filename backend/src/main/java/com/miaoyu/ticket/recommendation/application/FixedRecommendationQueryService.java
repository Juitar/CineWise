package com.miaoyu.ticket.recommendation.application;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import com.miaoyu.ticket.common.error.BusinessException;
import com.miaoyu.ticket.recommendation.domain.PurchaseCandidateValidator;
import org.springframework.stereotype.Service;

/**
 * 第一期固定推荐查询。
 *
 * <p>A 的公开场次 DTO 尚未提供可校验的 expiresAt 时，本服务只能返回不可购的内容候选。它不会调用
 * Controller、Mapper、模型或其他工具，也不会猜测 showId、价格、开场时间和库存。</p>
 */
@Service
public class FixedRecommendationQueryService {

    /*
     * 查询先尝试 A 的公开场次事实，再执行完整性和时效过滤。
     * A 查询无结果或不可用时，返回不可购内容候选。
     * 不可购结果不是异常，主控可据此继续收集条件。
     * D 不持久化场次、价格、库存或座位。
     * 排序在这里固定，避免底层数据库顺序影响推荐结果。
     * 公共结果的到期时间取候选中的最早值。
     * 内容目录的时间窗口只用于回退结果。
     * 用户明确条件由 Command 传入。
     * 当前阶段不读取长期画像。
     * 当前阶段不计算推荐评分。
     * 当前阶段不写推荐记录。
     * 当前阶段不执行外部网络调用。
     * 票务最终校验仍在 A 的建单流程。
     * 缺失场次不会阻断内容候选返回。
     * 过期场次不会进入可购结果。
     * 价格格式不合法不会进入可购结果。
     * 结果来源始终被保留。
     * 相同输入和时钟保持同样顺序。
     */

    private static final String SHOWTIME = "SHOWTIME";

    private final FixedRecommendationCatalogProvider catalogProvider;
    private final RecommendationShowtimeQueryPort showtimeQueryPort;
    private final Clock clock;

    public FixedRecommendationQueryService(
            FixedRecommendationCatalogProvider catalogProvider,
            RecommendationShowtimeQueryPort showtimeQueryPort,
            Clock clock) {
        this.catalogProvider = catalogProvider;
        this.showtimeQueryPort = showtimeQueryPort;
        this.clock = clock;
    }

    /**
     * 返回固定顺序的内容候选，并显式说明缺少 A 的场次事实。
     *
     * <p>同一输入和业务时钟会生成相同结果；候选顺序由 List.of 固定，避免后续主控测试受到集合遍历顺序
     * 影响。</p>
     */
    public FixedRecommendationResult query(RecommendationQuery query) {
        // 工具和未来 REST 都只能经此用例进入推荐规则，不能直接读取 A 的持久化层。
        // 先加载版本化目录；这一步不访问票务库或其他模块的持久化实现。
        FixedRecommendationCatalog catalog = catalogProvider.load();
        // 使用业务 Clock 保证测试和生产都以同一时间来源判断结果有效期。
        Instant dataAt = clock.instant();
        // 固定目录的有效期只描述目录本身，不表示场次、价格或库存仍然有效。
        Instant expiresAt = dataAt.plus(Duration.ofMinutes(catalog.validForMinutes()));
        List<EligibleCandidate> eligibleCandidates = findPurchaseCandidates(query, dataAt);
        List<RecommendationCandidate> purchaseCandidates = eligibleCandidates.stream()
                .map(EligibleCandidate::candidate)
                .toList();
        if (!purchaseCandidates.isEmpty()) {
            // 公共结果窗口取最早到期候选，保证窗口内每个可购候选仍有效。
            Instant earliestExpiresAt = eligibleCandidates.stream()
                    .map(EligibleCandidate::expiresAt)
                    .min(Comparator.naturalOrder())
                    .orElseThrow();
            return new FixedRecommendationResult(
                    catalog.version(),
                    purchaseCandidates,
                    true,
                    List.of(),
                    catalog.source(),
                    dataAt,
                    earliestExpiresAt,
                    false);
        }
        // 仅回显调用方已校验的内容引用，禁止在没有 A 场次事实时拼出 showId 或价格。
        RecommendationCandidate candidate = new RecommendationCandidate(
                query.movieId(), query.cinemaId(), null, null, null, catalog.source(), false, false);
        // SHOWTIME 是唯一缺失因素，交由主控决定追问、刷新或展示非购票内容入口。
        return new FixedRecommendationResult(
                catalog.version(),
                List.of(candidate),
                false,
                List.of(SHOWTIME),
                catalog.source(),
                dataAt,
                expiresAt,
                false);
    }

    /** A 查询不可用、无结果或返回不完整事实时统一降级为不可购内容候选。 */
    private List<EligibleCandidate> findPurchaseCandidates(RecommendationQuery query, Instant now) {
        try {
            // A 的 Application Service 是场次、价格和库存事实的唯一入口。
            return showtimeQueryPort.querySaleable(query).stream()
                    // 任一动态字段不完整或已过期时，不允许生成可购候选。
                    .filter(candidate -> PurchaseCandidateValidator.isEligible(candidate, now))
                    // 显式排序防止数据库返回顺序变化影响固定推荐结果。
                    .sorted(Comparator.comparing(PurchaseCandidateValidator.PurchaseCandidate::startTime)
                            .thenComparing(PurchaseCandidateValidator.PurchaseCandidate::showId))
                    .map(candidate -> new EligibleCandidate(
                            new RecommendationCandidate(
                                    candidate.movieId(), candidate.cinemaId(), candidate.showId(), candidate.price(),
                                    candidate.startTime(), candidate.source(), false, true),
                            candidate.expiresAt()))
                    .toList();
        } catch (BusinessException exception) {
            // 查询不可用时保留不可购内容候选，不能把异常转化为虚构票务事实。
            return List.of();
        }
    }

    /** 保留 A 返回的过期时间，用于计算本次工具结果的共同有效期。 */
    private record EligibleCandidate(RecommendationCandidate candidate, Instant expiresAt) {
    }
}
