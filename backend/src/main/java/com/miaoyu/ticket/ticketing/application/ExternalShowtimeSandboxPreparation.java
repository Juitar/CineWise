package com.miaoyu.ticket.ticketing.application;

import java.time.LocalDateTime;

/**
 * 外部参考候选经过 A 的本地沙箱准入校验后的结果。
 *
 * <p>只有 {@link Status#READY} 可以在后续事务中创建本地影厅、场次和座位；其他状态一律零写入，
 * 防止过期或降级的第三方资料污染票务交易事实。</p>
 */
public record ExternalShowtimeSandboxPreparation(Status status, LocalDateTime estimatedEndTime) {

    /** 准入结果同时是后续日志和批量导入报告使用的稳定业务语义。 */
    public enum Status {
        READY,
        NOT_SANDBOX_REFERENCE_ELIGIBLE,
        EXPIRED,
        DEGRADED,
        INVALID_IDENTITY,
        INVALID_DURATION,
        ALREADY_STARTED
    }

    /** 返回一个明确的跳过结果，避免以 null 或异常表达正常的候选过滤。 */
    public static ExternalShowtimeSandboxPreparation skipped(Status status) {
        return new ExternalShowtimeSandboxPreparation(status, null);
    }

    /** 本地预计结束时间仅在候选可安全导入时存在。 */
    public static ExternalShowtimeSandboxPreparation ready(LocalDateTime estimatedEndTime) {
        return new ExternalShowtimeSandboxPreparation(Status.READY, estimatedEndTime);
    }
}
