package com.miaoyu.ticket.ticketing.application;

import com.miaoyu.ticket.common.config.ClockConfiguration;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.Objects;
import org.springframework.stereotype.Component;

/**
 * 把 D 的外部开场参考过滤为可由 A 创建本地沙箱场次的输入。
 *
 * <p>外部候选永远不直接形成可售库存。本策略只决定是否允许进入后续导入事务，并以影片时长计算
 * A 的本地预计结束时间；该时间不得在 UI 或日志中描述为外部真实散场时间。</p>
 */
@Component
public class ExternalShowtimeSandboxReferencePolicy {

    private final Clock clock;

    public ExternalShowtimeSandboxReferencePolicy(Clock clock) {
        this.clock = Objects.requireNonNull(clock, "clock must not be null");
    }

    /**
     * 验证候选的来源、时效和最小身份，并计算业务时区下的本地预计结束时间。
     *
     * <p>候选的过期、降级或截断语义必须在 A 写库之前被拒绝；未来的持久化导入服务只能消费
     * {@code READY} 结果，不能自行绕过本策略重新判断。</p>
     */
    public ExternalShowtimeSandboxPreparation prepare(ExternalShowtimeSandboxReference reference) {
        if (reference == null || !reference.sandboxReferenceEligible()) {
            return ExternalShowtimeSandboxPreparation.skipped(
                    ExternalShowtimeSandboxPreparation.Status.NOT_SANDBOX_REFERENCE_ELIGIBLE);
        }
        if (reference.expired() || reference.expiresAt() == null
                || !reference.expiresAt().toInstant().isAfter(clock.instant())) {
            return ExternalShowtimeSandboxPreparation.skipped(ExternalShowtimeSandboxPreparation.Status.EXPIRED);
        }
        if (reference.degraded() || reference.fallback()) {
            return ExternalShowtimeSandboxPreparation.skipped(ExternalShowtimeSandboxPreparation.Status.DEGRADED);
        }
        if (!hasValidIdentity(reference)) {
            return ExternalShowtimeSandboxPreparation.skipped(
                    ExternalShowtimeSandboxPreparation.Status.INVALID_IDENTITY);
        }
        if (reference.durationMinutes() == null || reference.durationMinutes() <= 0) {
            return ExternalShowtimeSandboxPreparation.skipped(
                    ExternalShowtimeSandboxPreparation.Status.INVALID_DURATION);
        }
        if (!reference.startTime().toInstant().isAfter(clock.instant())) {
            return ExternalShowtimeSandboxPreparation.skipped(
                    ExternalShowtimeSandboxPreparation.Status.ALREADY_STARTED);
        }

        LocalDateTime estimatedEndTime = reference.startTime()
                .atZoneSameInstant(ClockConfiguration.BUSINESS_ZONE_ID)
                .toLocalDateTime()
                .plusMinutes(reference.durationMinutes());
        return ExternalShowtimeSandboxPreparation.ready(estimatedEndTime);
    }

    /** 外部三元键和本地内容映射必须完整，避免按片名或影院名称猜测票务归属。 */
    private static boolean hasValidIdentity(ExternalShowtimeSandboxReference reference) {
        return isNotBlank(reference.provider())
                && isNotBlank(reference.externalCinemaId())
                && isNotBlank(reference.externalShowId())
                && reference.movieId() != null
                && reference.movieId() > 0
                && reference.cinemaId() != null
                && reference.cinemaId() > 0
                && reference.startTime() != null
                && reference.dataAt() != null;
    }

    private static boolean isNotBlank(String value) {
        return value != null && !value.isBlank();
    }
}
