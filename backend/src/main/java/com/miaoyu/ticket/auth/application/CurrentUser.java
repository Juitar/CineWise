package com.miaoyu.ticket.auth.application;

/** 跨模块只读身份摘要；C 的认证实现负责从 SecurityContext 构造。 */
public record CurrentUser(Long userId, RoleCode role, long tokenVersion) {
}
