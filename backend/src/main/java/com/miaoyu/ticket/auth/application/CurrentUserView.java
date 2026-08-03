package com.miaoyu.ticket.auth.application;

import com.miaoyu.ticket.auth.domain.AccountStatus;

/** 浏览器可见身份摘要与内部 CurrentUser 分离，不暴露完整邮箱和 tokenVersion。 */
public record CurrentUserView(
        String id,
        RoleCode role,
        String nickname,
        String emailMasked,
        boolean emailVerified,
        AccountStatus status,
        String privacyPolicyVersion) {
}
