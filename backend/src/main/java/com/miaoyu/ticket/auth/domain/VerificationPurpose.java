package com.miaoyu.ticket.auth.domain;

/** 邮箱验证码用途决定摘要、冷却和消费边界，跨用途验证码不可混用。 */
public enum VerificationPurpose {
    REGISTER,
    LOGIN,
    RESET_PASSWORD
}
