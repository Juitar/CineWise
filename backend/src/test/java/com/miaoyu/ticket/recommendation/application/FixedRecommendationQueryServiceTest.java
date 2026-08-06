package com.miaoyu.ticket.recommendation.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

import com.miaoyu.ticket.common.error.BusinessException;
import com.miaoyu.ticket.common.error.ErrorCode;
import com.miaoyu.ticket.content.domain.ContentSourceType;
import com.miaoyu.ticket.auth.application.CurrentUser;
import com.miaoyu.ticket.auth.application.CurrentUserAccessor;
import com.miaoyu.ticket.auth.application.RoleCode;
import com.miaoyu.ticket.profile.application.ProfileQueryService;
import com.miaoyu.ticket.profile.application.ProfileSummary;
import com.miaoyu.ticket.profile.domain.ProfileTagPolarity;
import com.miaoyu.ticket.profile.domain.ProfileTagSource;
import com.miaoyu.ticket.profile.domain.ProfileTagType;
import com.miaoyu.ticket.recommendation.domain.PurchaseCandidateValidator.PurchaseCandidate;
import java.lang.reflect.Constructor;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneOffset;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;

class FixedRecommendationQueryServiceTest {

    /*
     * 应用服务测试覆盖两种明确结果：引用 A 的可购事实，或返回不可购内容候选。
     * 固定时钟让 expiresAt 和候选排序可重复验证。
     * 测试端口使用内存实现，不依赖票务数据库。
     * 任何缺失场次都不能让 D 自己填充价格或 showId。
     * 时段参数在进入查询前必须完整且有序。
     * 这些断言是后续 B 工具联调的基础。
     */

    private static final Instant NOW = Instant.parse("2026-08-03T00:00:00Z");

    @Test
    void shouldReturnStableContentOnlyCandidateWhenShowtimeFactsAreUnavailable() {
        FixedRecommendationQueryService service = new FixedRecommendationQueryService(
                () -> new FixedRecommendationCatalog(
                        "fixed-rec-v1", "FIXED_RECOMMENDATION", ContentSourceType.MOCK, 360),
                query -> java.util.List.of(),
                Clock.fixed(NOW, ZoneOffset.UTC));
        RecommendationQuery query = new RecommendationQuery("101", "201", LocalDate.of(2026, 8, 3), null, null);

        FixedRecommendationResult first = service.query(query);
        FixedRecommendationResult second = service.query(query);

        // 同一输入和时钟必须得到完全相同的候选，避免 Agent 回归结果不稳定。
        assertThat(first).isEqualTo(second);
        // 固定目录版本不能被运行时改变。
        assertThat(first.algorithmVersion()).isEqualTo("fixed-rec-v1");
        // 没有 A 事实时禁止可购。
        assertThat(first.purchaseEligible()).isFalse();
        // 明确把缺失原因交给主控。
        assertThat(first.missingFactors()).containsExactly("SHOWTIME");
        assertThat(first.candidates()).singleElement().satisfies(candidate -> {
            // 内容引用仍与用户条件一致。
            assertThat(candidate.movieId()).isEqualTo("101");
            // 影院引用仍与用户条件一致。
            assertThat(candidate.cinemaId()).isEqualTo("201");
            // 内容候选不能伪装成可购。
            assertThat(candidate.purchaseEligible()).isFalse();
            // 固定结果必须标明来源。
            assertThat(candidate.source()).isEqualTo("FIXED_RECOMMENDATION");
        });
    }

    @Test
    void shouldUseOnlyTicketingFactsWhenSaleableShowtimeIsAvailable() {
        PurchaseCandidate ticketingCandidate = new PurchaseCandidate(
                "301", "101", "201", "45.00", NOW.plusSeconds(3_600), NOW.plusSeconds(1_800), "TICKETING:MOCK");
        FixedRecommendationQueryService service = new FixedRecommendationQueryService(
                () -> new FixedRecommendationCatalog(
                        "fixed-rec-v1", "FIXED_RECOMMENDATION", ContentSourceType.MOCK, 360),
                query -> java.util.List.of(ticketingCandidate),
                Clock.fixed(NOW, ZoneOffset.UTC));

        FixedRecommendationResult result = service.query(
                new RecommendationQuery("101", "201", LocalDate.of(2026, 8, 3), null, null));

        // 可购标识只能在完整票务事实通过校验后出现。
        assertThat(result.purchaseEligible()).isTrue();
        // 可购场景不应保留 SHOWTIME 缺失标记。
        assertThat(result.missingFactors()).isEmpty();
        // 公共有效期必须取 A 返回的场次过期时间，不能误用开场时间。
        assertThat(result.expiresAt()).isEqualTo(NOW.plusSeconds(1_800));
        assertThat(result.candidates()).singleElement().satisfies(candidate -> {
            // showId 完全来自 A 的返回。
            assertThat(candidate.showId()).isEqualTo("301");
            // 价格完全来自 A 的 basePrice 映射。
            assertThat(candidate.price()).isEqualTo("45.00");
            // 开场时间完全来自 A 的返回。
            assertThat(candidate.startTime()).isEqualTo(NOW.plusSeconds(3_600));
        });
    }

    @Test
    void shouldRejectUnpairedOrInvalidTimeRangeBeforeReturningCandidate() {
        assertThatIllegalArgumentException().isThrownBy(() -> new RecommendationQuery(
                "101", "201", LocalDate.of(2026, 8, 3), LocalTime.of(18, 0), null));
        assertThatIllegalArgumentException().isThrownBy(() -> new RecommendationQuery(
                "101", "201", LocalDate.of(2026, 8, 3), LocalTime.of(20, 0), LocalTime.of(20, 0)));
    }

    @Test
    void shouldReturnContentOnlyCandidateWhenTicketingShowtimeQueryIsUnavailable() {
        FixedRecommendationQueryService service = new FixedRecommendationQueryService(
                () -> new FixedRecommendationCatalog(
                        "fixed-rec-v1", "FIXED_RECOMMENDATION", ContentSourceType.MOCK, 360),
                query -> {
                    throw new BusinessException(new ErrorCode() {
                        @Override public int code() { return 201001; }
                        @Override public String message() { return "场次查询暂不可用"; }
                        @Override public HttpStatus httpStatus() { return HttpStatus.SERVICE_UNAVAILABLE; }
                    });
                },
                Clock.fixed(NOW, ZoneOffset.UTC));

        FixedRecommendationResult result = service.query(
                new RecommendationQuery("101", "201", LocalDate.of(2026, 8, 3), null, null));

        // A 的公开查询异常不能被替换成 D 自己编造的场次、价格或可购卡片。
        assertThat(result.purchaseEligible()).isFalse();
        assertThat(result.missingFactors()).containsExactly("SHOWTIME");
        assertThat(result.candidates()).singleElement().satisfies(candidate -> {
            assertThat(candidate.showId()).isNull();
            assertThat(candidate.price()).isNull();
        });
    }

    @Test
    void shouldRecordOnlyMatchingCinemaProfileAsMinimalEvidence() {
        ProfileQueryService profileQueryService = new ProfileQueryService(null, null, null, null) {
            @Override
            public ProfileSummary assembleSummary(long userId) {
                return new ProfileSummary(true, 3L, NOW, java.util.List.of(new ProfileSummary.Tag(
                        ProfileTagType.CINEMA,
                        "201",
                        ProfileTagPolarity.LIKE,
                        new java.math.BigDecimal("0.900"),
                        new java.math.BigDecimal("0.900"),
                        ProfileTagSource.MANUAL,
                        NOW)));
            }
        };
        CurrentUserAccessor currentUserAccessor = () -> new CurrentUser(99L, RoleCode.USER, 0L);
        FixedRecommendationQueryService service = new FixedRecommendationQueryService(
                () -> new FixedRecommendationCatalog(
                        "fixed-rec-v1", "FIXED_RECOMMENDATION", ContentSourceType.MOCK, 360),
                query -> java.util.List.of(),
                Clock.fixed(NOW, ZoneOffset.UTC),
                profileQueryService,
                currentUserAccessor);

        FixedRecommendationResult result = service.query(
                new RecommendationQuery("101", "201", LocalDate.of(2026, 8, 3), null, null));

        assertThat(result.profileApplied()).isTrue();
        assertThat(result.profileEvidence()).singleElement().satisfies(evidence -> {
            assertThat(evidence.type()).isEqualTo(ProfileTagType.CINEMA);
            assertThat(evidence.value()).isEqualTo("201");
            assertThat(evidence.source()).isEqualTo(ProfileTagSource.MANUAL);
        });
    }

    @Test
    void shouldUseProfileAwareConstructorForSpringInjection() {
        Constructor<?>[] constructors = FixedRecommendationQueryService.class.getConstructors();

        assertThat(constructors)
                .filteredOn(constructor -> constructor.isAnnotationPresent(Autowired.class))
                .singleElement()
                .satisfies(constructor -> assertThat(constructor.getParameterCount()).isEqualTo(5));
    }
}
