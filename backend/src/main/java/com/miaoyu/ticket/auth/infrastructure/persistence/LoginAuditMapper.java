package com.miaoyu.ticket.auth.infrastructure.persistence;

import com.miaoyu.ticket.auth.application.LoginAuditRepository;
import java.time.LocalDateTime;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/** 登录日志只有新增和按到期时间批量清理两种写操作，不提供修改或单条删除入口。 */
@Mapper
public interface LoginAuditMapper {

    @Insert("""
            INSERT INTO sys_login_log (
                id, user_id, login_type, success, failure_code, ip_hash,
                user_agent_summary, trace_id, create_time
            ) VALUES (
                #{record.id}, #{record.userId}, #{record.loginType}, #{record.success}, #{record.failureCode},
                #{record.ipHash}, #{record.userAgentSummary}, #{record.traceId}, #{record.createTime}
            )
            """)
    void insert(@Param("record") LoginAuditRepository.LoginAuditRecord record);

    /** V006 的 idx_login_cleanup_create_time 可直接支持该范围删除。 */
    @Delete("DELETE FROM sys_login_log WHERE create_time < #{cutoff}")
    int deleteCreatedBefore(@Param("cutoff") LocalDateTime cutoff);
}
