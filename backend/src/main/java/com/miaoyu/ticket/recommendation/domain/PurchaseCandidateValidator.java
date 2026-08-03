package com.miaoyu.ticket.recommendation.domain;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Objects;

/**
 * 可购候选的最小完整性和时效校验。
 *
 * <p>此规则只验证未来由 A 提供的事实，不负责查询场次、更改价格或补齐字段。任一字段缺失、价格格式
 * 不合规或已过期时，调用方必须排除该候选。</p>
 */
public final class PurchaseCandidateValidator {

    private PurchaseCandidateValidator() {
    }

    /** 判断候选能否作为可购方案；失败统一返回 false，避免将不完整事实带入卡片。 */
    public static boolean isEligible(PurchaseCandidate candidate, Instant now) {
        // 这不是参数修复器：任何缺失事实都直接排除，不能尝试补默认价格或场次。
        Objects.requireNonNull(candidate, "candidate 不能为空");
        // 当前时间由调用方传入，测试能够稳定覆盖临界到期场景。
        Objects.requireNonNull(now, "now 不能为空");
        // showId 由 A 返回；D 只验证其格式，不创建或替换该值。
        // movieId 和 cinemaId 必须同时存在，避免出现跨影片或跨影院的错误引用。
        return isBusinessId(candidate.showId())
                && isBusinessId(candidate.movieId())
                && isBusinessId(candidate.cinemaId())
                && isTwoDecimalPrice(candidate.price())
                && candidate.startTime() != null
                // 开场和失效时间都缺一不可；只有开场时间不能说明查询结果是否仍可使用。
                && candidate.expiresAt() != null
                // 来源是排查动态事实问题的依据，空来源不能进入可购卡片。
                && candidate.source() != null
                && !candidate.source().isBlank()
                // 到达 expiresAt 的瞬间即不可购，使用严格大于避免边界竞态。
                && candidate.expiresAt().isAfter(now);
    }

    /** A 的公开查询事实进入推荐前的类型化承载；D 不在这里保存或构造任何票务事实。 */
    public record PurchaseCandidate(
            String showId,
            String movieId,
            String cinemaId,
            String price,
            Instant startTime,
            Instant expiresAt,
            String source) {
        // 该 DTO 只承载 A 已查询到的事实，后续适配器负责把 basePrice 映射为 price。
    }

    private static boolean isBusinessId(String value) {
        // 统一按十进制字符串验证，避免长整型在跨端传输时丢失精度。
        // 不接受零值，因为零不是 CineWise 的有效业务主键。
        return value != null && value.matches("[1-9]\\d*");
    }

    private static boolean isTwoDecimalPrice(String value) {
        // API 金额固定为两位小数字符串，不接受 Number、科学计数法或一位小数。
        if (value == null || !value.matches("\\d+\\.\\d{2}")) {
            return false;
        }
        try {
            // 正则只保证外形；BigDecimal 再确认该金额不是负数。
            // 不在这里比较预算，预算过滤属于后续确定性评分规则。
            return new BigDecimal(value).signum() >= 0;
        } catch (NumberFormatException exception) {
            // 即使出现极端数值格式，也按不可购处理，不能把异常传成可购卡片。
            // 查询工具保持只读，不会因这类异常发起补偿或重试写操作。
            return false;
        }
    }
}
