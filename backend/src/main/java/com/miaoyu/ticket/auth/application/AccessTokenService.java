package com.miaoyu.ticket.auth.application;

import com.miaoyu.ticket.auth.domain.AuthUser;
import java.time.Instant;
import java.util.Optional;

/** JWT 的格式和签名由基础设施实现，应用层只签发认证账号快照。 */
public interface AccessTokenService {

    IssuedAccessToken issue(AuthUser user);

    /** 续签保留首次会话起点；达到绝对上限时返回空，不生成新 JWT。 */
    Optional<IssuedAccessToken> renew(AuthUser user, Instant sessionStartedAt);
}
