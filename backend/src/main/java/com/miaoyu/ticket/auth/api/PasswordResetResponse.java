package com.miaoyu.ticket.auth.api;

/** 成功响应只确认密码已改变，不暴露内部 tokenVersion。 */
public record PasswordResetResponse(boolean changed) {
}
