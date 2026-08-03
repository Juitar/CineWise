package com.miaoyu.ticket.auth.application;

/** 登录成功后由 API 层把 accessToken 写入 HttpOnly Cookie，只向响应体返回用户摘要。 */
public record LoginResult(String accessToken, CurrentUserView currentUser) {
}
