package com.miaoyu.ticket.auth.application;

import java.time.Clock;
import java.time.LocalDateTime;
import org.springframework.stereotype.Service;

/** 登录审计仅按正数保留期清理，不提供按用户或单条日志删除能力。 */
@Service
public class LoginAuditCleanupService {

    private final LoginAuditRepository auditRepository;
    private final Clock clock;

    public LoginAuditCleanupService(LoginAuditRepository auditRepository, Clock clock) {
        this.auditRepository = auditRepository;
        this.clock = clock;
    }

    public int deleteExpiredLogs(int retentionDays) {
        if (retentionDays < 1) {
            throw new IllegalArgumentException("retentionDays must be positive");
        }
        LocalDateTime cutoff = LocalDateTime.now(clock).minusDays(retentionDays);
        return auditRepository.deleteCreatedBefore(cutoff);
    }
}
