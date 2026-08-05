package com.miaoyu.ticket.auth.application;

/** 验证码受理结果只返回冷却和有效期，不返回账号状态或验证码。 */
public record SendEmailCodeResult(long cooldownSeconds, long expiresInSeconds) {
}
