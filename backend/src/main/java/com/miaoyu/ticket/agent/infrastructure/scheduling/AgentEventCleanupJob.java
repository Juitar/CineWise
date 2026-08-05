package com.miaoyu.ticket.agent.infrastructure.scheduling;

import com.miaoyu.ticket.agent.application.persistence.AgentEventCleanupService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** 每日只调用 Agent 应用服务清理到期轨迹，不读取或记录事件载荷。 */
@Component
public class AgentEventCleanupJob {
    private static final Logger LOGGER = LoggerFactory.getLogger(AgentEventCleanupJob.class);
    private final AgentEventCleanupService cleanupService;

    public AgentEventCleanupJob(AgentEventCleanupService cleanupService) {
        this.cleanupService = cleanupService;
    }

    @Scheduled(cron = "${cinewise.agent.event-cleanup-cron:0 30 2 * * *}")
    public void cleanupExpiredEvents() {
        int count = cleanupService.cleanupExpiredRuns();
        if (count > 0) {
            LOGGER.info("已清理到期 Agent 运行, count={}", count);
        }
    }
}
