package com.miaoyu.ticket.auth.domain;

/** 账号状态直接对应 V006 的数据库约束。 */
public enum AccountStatus {
    NORMAL,
    DISABLED,
    LOCKED
}
