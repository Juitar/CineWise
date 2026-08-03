package com.miaoyu.ticket.auth.infrastructure.scheduling;

import com.miaoyu.ticket.auth.application.LoginAuditCleanupService;
import com.miaoyu.ticket.auth.infrastructure.config.AuthProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** 每次只调用认证应用服务执行到期清理，调度器本身不接触 Mapper。 */
@Component
public class LoginAuditCleanupJob {

    private static final Logger LOGGER = LoggerFactory.getLogger(LoginAuditCleanupJob.class);
    private final LoginAuditCleanupService cleanupService;
    private final AuthProperties properties;

    public LoginAuditCleanupJob(LoginAuditCleanupService cleanupService, AuthProperties properties) {
        this.cleanupService = cleanupService;
        this.properties = properties;
    }

    @Scheduled(fixedDelayString = "${cinewise.auth.login-log-cleanup-delay-milliseconds}")
    public void cleanExpiredLoginLogs() {
        int deleted = cleanupService.deleteExpiredLogs(properties.loginLogRetentionDays());
        if (deleted > 0) {
            LOGGER.info("已清理到期登录日志, count={}", deleted);
        }
    }
}
