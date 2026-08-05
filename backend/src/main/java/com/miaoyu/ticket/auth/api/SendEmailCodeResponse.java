package com.miaoyu.ticket.auth.api;

import com.miaoyu.ticket.auth.application.SendEmailCodeResult;

/** 验证码发送响应只暴露服务端时间窗口。 */
public record SendEmailCodeResponse(long cooldownSeconds, long expiresInSeconds) {

    static SendEmailCodeResponse from(SendEmailCodeResult result) {
        return new SendEmailCodeResponse(result.cooldownSeconds(), result.expiresInSeconds());
    }
}
