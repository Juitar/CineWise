package com.miaoyu.ticket.ticketing.application;

import com.miaoyu.ticket.content.application.ExternalShowtimeQueryPort;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import org.springframework.stereotype.Service;

/**
 * 将 D 的公开排期快照转换为 A 的本地沙箱导入计划。
 *
 * <p>该服务不访问 D 的快照表，也不写 A 的影厅、场次或座位。它只消费
 * {@code SANDBOX_REFERENCE}，并把 D 的 DTO 显式映射为 A 自己的输入模型；真实写入留给后续事务服务。</p>
 */
@Service
public class ExternalShowtimeSandboxImportService {

    private final ExternalShowtimeQueryPort queryPort;
    private final ExternalShowtimeSandboxReferencePolicy referencePolicy;

    public ExternalShowtimeSandboxImportService(
            ExternalShowtimeQueryPort queryPort,
            ExternalShowtimeSandboxReferencePolicy referencePolicy) {
        this.queryPort = Objects.requireNonNull(queryPort, "queryPort must not be null");
        this.referencePolicy = Objects.requireNonNull(referencePolicy, "referencePolicy must not be null");
    }

    /**
     * 查询并生成本地沙箱导入计划。
     *
     * <p>成功候选发生截断时整批不生成计划，避免调用方把部分候选误当作完整排期；拒绝列表只用于
     * D 的诊断，不会被转换或导入。</p>
     */
    public ExternalShowtimeSandboxImportPlan prepare(LocalDate showDate, List<Long> cinemaIds) {
        ExternalShowtimeQueryPort.QueryResult result = queryPort.query(
                new ExternalShowtimeQueryPort.Query(showDate, cinemaIds));
        if (result.truncated()) {
            return new ExternalShowtimeSandboxImportPlan(List.of(), true);
        }

        List<ExternalShowtimeSandboxImportPlan.Entry> entries = new ArrayList<>();
        for (ExternalShowtimeQueryPort.ExternalShowtimeSnapshot snapshot : result.snapshots()) {
            if (snapshot.qualityStatus() != ExternalShowtimeQueryPort.QualityStatus.SANDBOX_REFERENCE) {
                continue;
            }
            if (snapshot.externalShowtimeKey() == null) {
                // D 的异常候选不能绕过 A 的身份准入，更不能因空键中断同批其他导入计划。
                continue;
            }
            ExternalShowtimeSandboxReference reference = toReference(snapshot);
            ExternalShowtimeSandboxPreparation preparation = referencePolicy.prepare(reference);
            if (preparation.status() == ExternalShowtimeSandboxPreparation.Status.READY) {
                entries.add(new ExternalShowtimeSandboxImportPlan.Entry(
                        reference, preparation.estimatedEndTime()));
            }
        }
        return new ExternalShowtimeSandboxImportPlan(entries, false);
    }

    /** 显式映射 D 的公开 DTO，避免把 D 的内容层类型泄漏进 A 的票务持久化边界。 */
    private static ExternalShowtimeSandboxReference toReference(
            ExternalShowtimeQueryPort.ExternalShowtimeSnapshot snapshot) {
        return new ExternalShowtimeSandboxReference(
                snapshot.externalShowtimeKey().provider(),
                snapshot.source(),
                snapshot.externalCinemaId(),
                snapshot.externalShowId(),
                snapshot.movieId(),
                snapshot.cinemaId(),
                snapshot.startTime(),
                snapshot.durationMinutes(),
                snapshot.auditoriumText(),
                snapshot.dataAt(),
                snapshot.expiresAt(),
                true,
                snapshot.isExpired(),
                snapshot.degraded(),
                snapshot.fallbackType() != null);
    }
}
