package com.miaoyu.ticket.auth.application;

/** 业务模块读取当前身份的唯一公共端口，禁止从请求体接收可信 userId。 */
public interface CurrentUserAccessor {

    CurrentUser requireCurrentUser();

    default Long requireCurrentUserId() {
        return requireCurrentUser().userId();
    }
}
