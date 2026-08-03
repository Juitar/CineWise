package com.miaoyu.ticket.auth.infrastructure.persistence;

import java.time.LocalDateTime;

/** MyBatis 行对象仅存在于持久化层，字段与 V006 显式 SQL 别名一一对应。 */
public record AuthUserRow(
        long id,
        String email,
        String passwordHash,
        String nickname,
        String roleCode,
        String status,
        boolean emailVerified,
        long tokenVersion,
        String privacyPolicyVersion,
        LocalDateTime privacyAcceptedAt) {
}
