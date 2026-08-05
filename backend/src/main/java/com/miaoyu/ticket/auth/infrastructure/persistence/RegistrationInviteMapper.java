package com.miaoyu.ticket.auth.infrastructure.persistence;

import com.miaoyu.ticket.auth.domain.RegistrationInvite;
import java.time.LocalDateTime;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

/** 邀请码最后一次使用由单条条件更新决定，不依赖 JVM 锁。 */
@Mapper
public interface RegistrationInviteMapper {

    @Select("""
            SELECT id, code_hash, status, max_uses, used_count, valid_from, expire_time, version
              FROM sys_registration_invite
             WHERE code_hash = #{codeHash}
             LIMIT 1
            """)
    RegistrationInviteRow findByCodeHash(@Param("codeHash") String codeHash);

    @Insert("""
            INSERT INTO sys_registration_invite (
                id, code_hash, status, max_uses, used_count, valid_from,
                expire_time, version, create_time, update_time
            )
            SELECT
                #{invite.id}, #{invite.codeHash}, #{invite.status}, #{invite.maxUses},
                #{invite.usedCount}, #{invite.validFrom}, #{invite.expireTime}, #{invite.version},
                #{createTime}, #{createTime}
            FROM DUAL
            WHERE NOT EXISTS (
                SELECT 1 FROM sys_registration_invite WHERE code_hash = #{invite.codeHash}
            )
            """)
    int insertIfAbsent(
            @Param("invite") RegistrationInvite invite,
            @Param("createTime") LocalDateTime createTime);

    @Update("""
            UPDATE sys_registration_invite
               SET used_count = used_count + 1,
                   version = version + 1,
                   update_time = #{now}
             WHERE id = #{inviteId}
               AND version = #{expectedVersion}
               AND status = 'ENABLED'
               AND valid_from <= #{now}
               AND expire_time > #{now}
               AND used_count < max_uses
            """)
    int consume(
            @Param("inviteId") long inviteId,
            @Param("expectedVersion") long expectedVersion,
            @Param("now") LocalDateTime now);
}
