package com.miaoyu.ticket.recommendation.application;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import com.miaoyu.ticket.common.error.BusinessException;
import com.miaoyu.ticket.auth.application.CurrentUserAccessor;
import com.miaoyu.ticket.profile.application.ProfileQueryService;
import com.miaoyu.ticket.profile.application.ProfileSummary;
import com.miaoyu.ticket.profile.domain.ProfileTagPolarity;
import com.miaoyu.ticket.profile.domain.ProfileTagType;
import com.miaoyu.ticket.recommendation.domain.PurchaseCandidateValidator;
import org.springframework.beans.factory.annotation.Autowired;
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

    /**
     * 缺少场次事实时返回给上层的唯一缺失项。
     * 该值不表示影片或影院不存在，只表示当前不能安全进入购票流程。
     */
    private static final String SHOWTIME = "SHOWTIME";

    /** 目录只提供固定内容候选，不负责查询票务价格、库存或场次。 */
    private final FixedRecommendationCatalogProvider catalogProvider;

    /** A 提供的公开场次查询入口，是可购事实的唯一来源。 */
    private final RecommendationShowtimeQueryPort showtimeQueryPort;

    /** 统一业务时钟，避免同一轮推荐中的到期判断受系统时间波动影响。 */
    private final Clock clock;

    // 画像查询只读，不创建默认偏好设置。
    // 画像关闭时摘要不包含任何长期标签。
    // Redis 故障由画像模块自行回退，推荐不读取缓存实现。
    // 推荐只保留实际采用的标签，不复制完整摘要。
    // 当前影院条件始终优先于长期影院偏好。
    // 负向标签不会产生“采用画像”的解释证据。
    // 这里不保存用户行为、对话正文或精确位置。
    // 推荐结果不会因画像缺失而改变原有场次筛选。
    // 画像模块边界外不访问其 Mapper 或 Repository。
    // 认证失败的处理仍由调用入口按既有规则返回。

    /**
     * D 的只读画像摘要服务。
     * 它只返回已允许暴露的最小标签集合，不会把原始行为或会话内容交给推荐模块。
     */
    private final ProfileQueryService profileQueryService;

    /**
     * 当前用户必须由认证上下文取得，不能相信推荐请求中可能携带的用户标识。
     * 画像读取因此只会发生在当前认证用户自己的数据范围内。
     */
    private final CurrentUserAccessor currentUserAccessor;

    /**
     * 为既有不启用画像的单元测试保留的便捷构造器。
     * 生产 Bean 不使用它，避免新画像依赖被静默置空。
     */
    public FixedRecommendationQueryService(
            FixedRecommendationCatalogProvider catalogProvider,
            RecommendationShowtimeQueryPort showtimeQueryPort,
            Clock clock) {
        this(catalogProvider, showtimeQueryPort, clock, null, null);
    }

    /**
     * 生产环境必须注入画像查询和当前用户访问器；否则推荐会静默跳过已授权的画像。
     * 三参数构造器只为既有单元测试和不启用画像的调用方保留，不能作为 Spring 的装配入口。
     */
    @Autowired
    public FixedRecommendationQueryService(
            FixedRecommendationCatalogProvider catalogProvider,
            RecommendationShowtimeQueryPort showtimeQueryPort,
            Clock clock,
            ProfileQueryService profileQueryService,
            CurrentUserAccessor currentUserAccessor) {
        this.catalogProvider = catalogProvider;
        this.showtimeQueryPort = showtimeQueryPort;
        this.clock = clock;
        this.profileQueryService = profileQueryService;
        this.currentUserAccessor = currentUserAccessor;
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
        ProfileUsage profileUsage = resolveProfileUsage(query);
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
                    false,
                    profileUsage.applied(),
                    profileUsage.evidence());
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
                false,
                profileUsage.applied(),
                profileUsage.evidence());
    }

    /**
     * 只接受 A 返回的完整、未过期场次事实。
     * A 查询异常时保留内容候选，避免把暂时不可购误报成影片不可推荐。
     * 此处不构造任何场次、价格或库存的替代值。
     */
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

    /**
     * 当前请求已明确影院时，画像只能作为“为何这个影院仍被采用”的证据，不能覆盖本轮选择。
     * 未登录、画像关闭、没有匹配标签或负向标签都按未采用处理，推荐继续使用原有固定规则。
     * 只匹配当前影院的正向标签，其他偏好不会被顺带暴露到结果中。
     * 返回的证据仅用于解释本次采用，不会写回画像或影响后续请求。
     * 未注入画像依赖的兼容调用也保持原有推荐结果。
     */
    private ProfileUsage resolveProfileUsage(RecommendationQuery query) {
        if (profileQueryService == null || currentUserAccessor == null) {
            return ProfileUsage.notApplied();
        }
        ProfileSummary summary = profileQueryService.assembleSummary(currentUserAccessor.requireCurrentUserId());
        if (!summary.enabled()) {
            return ProfileUsage.notApplied();
        }
        return summary.tags().stream()
                .filter(tag -> tag.type() == ProfileTagType.CINEMA)
                .filter(tag -> tag.polarity() == ProfileTagPolarity.LIKE)
                .filter(tag -> query.cinemaId().equals(tag.value()))
                .findFirst()
                .map(tag -> new ProfileUsage(true, List.of(new ProfileRecommendationEvidence(
                        tag.type(), tag.value(), tag.polarity(), tag.source()))))
                .orElseGet(ProfileUsage::notApplied);
    }

    /**
     * 将 A 的候选和它本身的到期时间保持在一起。
     * 最终结果取最早到期时间，避免其中一个场次过期后整组结果仍被误用。
     */
    private record EligibleCandidate(RecommendationCandidate candidate, Instant expiresAt) {
    }

    /**
     * 仅保存本轮是否真正采用画像及其最小解释证据。
     * 未采用时必须为空集合，不能用历史标签填充展示字段。
     */
    private record ProfileUsage(boolean applied, List<ProfileRecommendationEvidence> evidence) {
        private static ProfileUsage notApplied() {
            return new ProfileUsage(false, List.of());
        }
    }
}
