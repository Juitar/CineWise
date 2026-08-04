package com.miaoyu.ticket.auth.api;

/** 重复登出也返回 true，客户端无需判断旧会话是否已经失效。 */
public record LogoutResponse(boolean loggedOut) {
}
