package com.miaoyu.ticket.auth.domain;

import java.time.LocalDateTime;

/** 邀请码使用记录同时承担注册请求恢复索引，不保存邀请码明文。 */
public record RegistrationInviteUse(
        long id,
        long inviteId,
        long userId,
        String clientRequestId,
        LocalDateTime usedAt) {
}
