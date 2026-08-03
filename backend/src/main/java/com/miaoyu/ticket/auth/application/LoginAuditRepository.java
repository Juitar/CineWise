package com.miaoyu.ticket.auth.application;

import com.miaoyu.ticket.auth.domain.LoginType;
import java.time.LocalDateTime;

/** 登录日志是独立的只追加审计；失败不得改变已经确定的登录结果。 */
public interface LoginAuditRepository {

    void append(LoginAuditRecord record);

    int deleteCreatedBefore(LocalDateTime cutoff);

    record LoginAuditRecord(
            long id,
            Long userId,
            LoginType loginType,
            boolean success,
            String failureCode,
            String ipHash,
            String userAgentSummary,
            String traceId,
            LocalDateTime createTime) {
    }
}
