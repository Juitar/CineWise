package com.miaoyu.ticket.auth.domain;

/** 邀请码只允许启用或禁用，时间和次数限制由同一条件更新复核。 */
public enum RegistrationInviteStatus {
    ENABLED,
    DISABLED
}
