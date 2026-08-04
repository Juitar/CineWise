package com.miaoyu.ticket.auth.infrastructure.security;

import com.miaoyu.ticket.auth.application.AuthErrorCode;
import com.miaoyu.ticket.auth.application.CurrentUser;
import com.miaoyu.ticket.auth.application.CurrentUserAccessor;
import com.miaoyu.ticket.common.error.BusinessException;
import java.util.Optional;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

/** 只接受认证过滤器写入的内部 CurrentUser，不信任请求参数或普通用户名字符串。 */
@Component
public class SecurityContextCurrentUserAccessor implements CurrentUserAccessor {

    @Override
    public CurrentUser requireCurrentUser() {
        return findCurrentUser().orElseThrow(() -> new BusinessException(AuthErrorCode.SESSION_INVALID));
    }

    @Override
    public Optional<CurrentUser> findCurrentUser() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated()) {
            return Optional.empty();
        }
        return authentication.getPrincipal() instanceof CurrentUser currentUser
                ? Optional.of(currentUser)
                : Optional.empty();
    }
}
