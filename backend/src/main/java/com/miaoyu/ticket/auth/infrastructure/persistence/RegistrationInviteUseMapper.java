package com.miaoyu.ticket.auth.infrastructure.persistence;

import com.miaoyu.ticket.auth.domain.RegistrationInviteUse;
import java.time.LocalDateTime;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

/** 使用记录依靠正式迁移中的 clientRequestId 和 userId 唯一键阻止重复注册。 */
@Mapper
public interface RegistrationInviteUseMapper {

    @Select("""
            SELECT id, invite_id, user_id, client_request_id, used_at
              FROM sys_registration_invite_use
             WHERE client_request_id = #{clientRequestId}
             LIMIT 1
            """)
    RegistrationInviteUseRow findByClientRequestId(@Param("clientRequestId") String clientRequestId);

    @Insert("""
            INSERT INTO sys_registration_invite_use (
                id, invite_id, user_id, client_request_id, used_at, create_time
            ) VALUES (
                #{use.id}, #{use.inviteId}, #{use.userId}, #{use.clientRequestId}, #{use.usedAt}, #{createTime}
            )
            """)
    void insert(@Param("use") RegistrationInviteUse use, @Param("createTime") LocalDateTime createTime);
}
