package com.miaoyu.ticket.auth.infrastructure.persistence;

import com.miaoyu.ticket.auth.domain.AuthUser;
import java.time.LocalDateTime;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

/** 认证账号 SQL 固定查询列，禁止读取或向其他模块暴露整行实体。 */
@Mapper
public interface AuthUserMapper {

    String AUTH_COLUMNS = """
            id, email, password_hash, nickname, role_code, status, email_verified,
            token_version, privacy_policy_version, privacy_accepted_at
            """;

    @Select("SELECT " + AUTH_COLUMNS + " FROM sys_user WHERE email = #{email} LIMIT 1")
    AuthUserRow findByEmail(@Param("email") String normalizedEmail);

    @Select("SELECT " + AUTH_COLUMNS + " FROM sys_user WHERE id = #{userId} LIMIT 1")
    AuthUserRow findById(@Param("userId") long userId);

    @Select("SELECT COUNT(*) FROM sys_user WHERE email = #{email}")
    int countByEmail(@Param("email") String normalizedEmail);

    /** tokenVersion 和乐观锁版本同时递增，避免登出静默覆盖并发账号更新。 */
    @Update("""
            UPDATE sys_user
               SET token_version = token_version + 1,
                   version = version + 1,
                   update_time = #{updateTime}
             WHERE id = #{userId}
               AND token_version = #{expectedTokenVersion}
            """)
    int incrementTokenVersion(
            @Param("userId") long userId,
            @Param("expectedTokenVersion") long expectedTokenVersion,
            @Param("updateTime") LocalDateTime updateTime);

    @Insert("""
            INSERT INTO sys_user (
                id, email, password_hash, nickname, role_code, status, email_verified,
                token_version, privacy_policy_version, privacy_accepted_at, version, create_time, update_time
            ) VALUES (
                #{user.id}, #{user.email}, #{user.passwordHash}, #{user.nickname}, #{user.role}, #{user.status},
                #{user.emailVerified}, #{user.tokenVersion}, #{user.privacyPolicyVersion},
                #{user.privacyAcceptedAt}, 0, #{createTime}, #{createTime}
            )
            """)
    void insert(@Param("user") AuthUser user, @Param("createTime") LocalDateTime createTime);
}
