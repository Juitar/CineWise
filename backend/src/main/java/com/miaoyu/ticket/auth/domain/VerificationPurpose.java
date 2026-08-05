package com.miaoyu.ticket.auth.domain;

/** 本期开放的邮箱验证码用途；密码重置待独立变更实现后再加入。 */
public enum VerificationPurpose {
    REGISTER,
    LOGIN
}
