package com.miaoyu.ticket.auth.application;

import java.util.Optional;

/** 业务模块读取当前身份的唯一公共端口，禁止从请求体接收可信 userId。 */
public interface CurrentUserAccessor {

    CurrentUser requireCurrentUser();

    default Optional<CurrentUser> findCurrentUser() {
        return Optional.ofNullable(requireCurrentUser());
    }

    default Long requireCurrentUserId() {
        return requireCurrentUser().userId();
    }
}
