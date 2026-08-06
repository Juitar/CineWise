package com.miaoyu.ticket.auth.application;

/** 密码重置命令只保留事务所需字段，不携带 Cookie、角色或用户 ID。 */
public record PasswordResetCommand(
        String clientRequestId, String email, String code, String newPassword) {
}
