package com.miaoyu.ticket.auth.application;

import com.miaoyu.ticket.auth.domain.AuthUser;
import java.time.LocalDateTime;
import java.util.Optional;

/** 认证应用层只通过该端口读取或改变账号，不依赖 MyBatis 类型。 */
public interface AuthUserRepository {

    Optional<AuthUser> findByEmail(String normalizedEmail);

    Optional<AuthUser> findById(long userId);

    /** 仅在当前版本匹配时递增，防止并发登出覆盖其他账号状态修改。 */
    boolean incrementTokenVersion(long userId, long expectedTokenVersion, LocalDateTime updateTime);

    /** 密码摘要和 tokenVersion 必须由同一条件更新提交，旧会话随改密立即失效。 */
    boolean resetPassword(
            long userId, long expectedTokenVersion, String passwordHash, LocalDateTime updateTime);

    boolean existsByEmail(String normalizedEmail);

    void create(AuthUser user, LocalDateTime createTime);
}
