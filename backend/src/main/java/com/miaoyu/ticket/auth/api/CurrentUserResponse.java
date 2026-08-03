package com.miaoyu.ticket.auth.api;

import com.miaoyu.ticket.auth.application.CurrentUserView;
import com.miaoyu.ticket.auth.application.RoleCode;
import com.miaoyu.ticket.auth.domain.AccountStatus;

/** REST 身份 DTO 不复用内部 CurrentUser，明确排除完整邮箱和会话版本。 */
public record CurrentUserResponse(
        String id,
        RoleCode role,
        String nickname,
        String emailMasked,
        boolean emailVerified,
        AccountStatus status,
        String privacyPolicyVersion) {

    public static CurrentUserResponse from(CurrentUserView view) {
        return new CurrentUserResponse(
                view.id(),
                view.role(),
                view.nickname(),
                view.emailMasked(),
                view.emailVerified(),
                view.status(),
                view.privacyPolicyVersion());
    }
}
