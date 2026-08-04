package com.miaoyu.ticket.auth.application;

import com.miaoyu.ticket.auth.domain.AuthUser;

/** JWT 的格式和签名由基础设施实现，应用层只签发认证账号快照。 */
public interface AccessTokenService {

    String issue(AuthUser user);
}
