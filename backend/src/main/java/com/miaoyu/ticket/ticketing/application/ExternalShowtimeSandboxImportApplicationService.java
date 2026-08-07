package com.miaoyu.ticket.ticketing.application;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import org.springframework.stereotype.Service;

/** A 对外的本地沙箱导入入口；D 查询在数据库事务之外完成。 */
@Service
public class ExternalShowtimeSandboxImportApplicationService {

    private final ExternalShowtimeSandboxImportService planningService;
    private final ExternalShowtimeSandboxPersistenceService persistenceService;

    public ExternalShowtimeSandboxImportApplicationService(ExternalShowtimeSandboxImportService planningService,
            ExternalShowtimeSandboxPersistenceService persistenceService) {
        this.planningService = planningService;
        this.persistenceService = persistenceService;
    }

    /**
     * 读取一批 D 候选并逐条导入。
     *
     * <p>查询结果被截断时不做任何写入；每一条真正写入都委派给独立事务服务，避免一个候选失败
     * 回滚已经安全完成的其他候选。</p>
     */
    public ImportResult importReferences(LocalDate showDate, List<Long> cinemaIds) {
        ExternalShowtimeSandboxImportPlan plan = planningService.prepare(showDate, cinemaIds);
        if (plan.truncated()) {
            return new ImportResult(List.of(), true);
        }
        List<Long> showIds = new ArrayList<>();
        for (ExternalShowtimeSandboxImportPlan.Entry entry : plan.entries()) {
            showIds.add(persistenceService.importEntry(entry));
        }
        return new ImportResult(showIds, false);
    }

    /** 导入结果只返回 A 本地场次 ID；外部三元键与候选原文不向调用方扩散。 */
    public record ImportResult(List<Long> showIds, boolean truncated) {
        public ImportResult {
            showIds = List.copyOf(showIds);
        }
    }
}
