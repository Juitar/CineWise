package com.miaoyu.ticket.auth.domain;

/** 验证码只有未使用、已使用和已失效三种持久化状态。 */
public enum VerificationCodeStatus {
    UNUSED,
    USED,
    INVALID
}
