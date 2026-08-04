package com.miaoyu.ticket.auth.domain;

import com.miaoyu.ticket.auth.application.RoleCode;
import java.time.LocalDateTime;

/** 认证用账号快照，只包含登录和会话校验所需字段。 */
public record AuthUser(
        long id,
        String email,
        String passwordHash,
        String nickname,
        RoleCode role,
        AccountStatus status,
        boolean emailVerified,
        long tokenVersion,
        String privacyPolicyVersion,
        LocalDateTime privacyAcceptedAt) {

    public boolean isActive() {
        return status == AccountStatus.NORMAL;
    }
}
