package com.miaoyu.ticket.auth.infrastructure.persistence;

import java.time.LocalDateTime;

/** 使用记录行只保存内部关联和请求编号。 */
public record RegistrationInviteUseRow(
        long id, long inviteId, long userId, String clientRequestId, LocalDateTime usedAt) {
}
