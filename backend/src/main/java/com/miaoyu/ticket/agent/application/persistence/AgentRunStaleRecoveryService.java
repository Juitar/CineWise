package com.miaoyu.ticket.agent.application.persistence;

import com.miaoyu.ticket.common.config.ClockConfiguration;
import java.time.Clock;
import java.time.LocalDateTime;
import org.springframework.stereotype.Service;

/** 启动和新消息提交前扫描陈旧运行；不是定时任务，也不重放工具。 */
@Service
public class AgentRunStaleRecoveryService {
    private static final int STALE_AFTER_SECONDS = 30;
    private static final int RECOVERY_BATCH_SIZE = 100;

    private final AgentRunRepository runRepository;
    private final AgentRunStaleRecoveryTransaction recoveryTransaction;
    private final Clock clock;

    public AgentRunStaleRecoveryService(
            AgentRunRepository runRepository, AgentRunStaleRecoveryTransaction recoveryTransaction, Clock clock) {
        this.runRepository = runRepository;
        this.recoveryTransaction = recoveryTransaction;
        this.clock = clock;
    }

    /** 只处理更新时间不晚于当前时间减 30 秒的 RUNNING 运行。 */
    public void recoverStaleRuns() {
        LocalDateTime cutoff = LocalDateTime.ofInstant(clock.instant(), ClockConfiguration.BUSINESS_ZONE_ID)
                .minusSeconds(STALE_AFTER_SECONDS);
        runRepository.findStaleRunningBefore(cutoff, RECOVERY_BATCH_SIZE).forEach(run -> {
            try {
                recoveryTransaction.recover(run);
            } catch (AgentStaleRecoveryConcurrentException ignored) {
                // CAS 失败表示当前记录已有新状态，不能覆盖；下一次扫描再按实际更新时间判断。
            }
        });
    }
}
