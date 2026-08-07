package com.miaoyu.ticket.ticketing.application;

import java.time.LocalDateTime;
import java.util.List;

/**
 * A 对一批 D 沙箱参考候选完成准入后的不可变导入计划。
 *
 * <p>计划只描述待写入的本地事实，不执行数据库操作。后续事务层必须再次使用外部三元键做幂等
 * 判断，不能把这个计划当成已经创建成功的场次。</p>
 */
public record ExternalShowtimeSandboxImportPlan(
        List<Entry> entries,
        boolean truncated) {

    public ExternalShowtimeSandboxImportPlan {
        entries = List.copyOf(entries);
    }

    /** 单个候选及其本地预计结束时间，预计时间不代表外部真实散场时间。 */
    public record Entry(
            ExternalShowtimeSandboxReference reference,
            LocalDateTime estimatedEndTime) {
    }
}
