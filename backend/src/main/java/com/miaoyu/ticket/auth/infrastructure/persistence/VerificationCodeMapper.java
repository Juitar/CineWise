package com.miaoyu.ticket.auth.infrastructure.persistence;

import com.miaoyu.ticket.auth.domain.EmailVerificationCode;
import com.miaoyu.ticket.auth.domain.VerificationPurpose;
import java.time.LocalDateTime;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

/** 验证码更新都带状态、时间和尝试次数条件，由数据库影响行数裁决并发结果。 */
@Mapper
public interface VerificationCodeMapper {

    @Update("""
            UPDATE sys_email_verify_code
               SET status = 'INVALID', update_time = #{updateTime}
             WHERE email = #{email}
               AND purpose = #{purpose}
               AND status = 'UNUSED'
            """)
    int invalidateActive(
            @Param("email") String normalizedEmail,
            @Param("purpose") VerificationPurpose purpose,
            @Param("updateTime") LocalDateTime updateTime);

    @Insert("""
            INSERT INTO sys_email_verify_code (
                id, email, purpose, code_hash, status, send_time, expire_time,
                used_time, attempt_count, create_time, update_time
            ) VALUES (
                #{code.id}, #{code.email}, #{code.purpose}, #{code.codeHash}, #{code.status},
                #{code.sendTime}, #{code.expireTime}, #{code.usedTime}, #{code.attemptCount},
                #{createTime}, #{createTime}
            )
            """)
    void insert(@Param("code") EmailVerificationCode code, @Param("createTime") LocalDateTime createTime);

    @Select("""
            SELECT id, email, purpose, code_hash, status, send_time, expire_time, used_time, attempt_count
              FROM sys_email_verify_code
             WHERE email = #{email}
               AND purpose = #{purpose}
               AND status = 'UNUSED'
               AND expire_time > #{now}
               AND attempt_count < #{maximumAttempts}
             ORDER BY send_time DESC, id DESC
             LIMIT 1
            """)
    VerificationCodeRow findLatestUsable(
            @Param("email") String normalizedEmail,
            @Param("purpose") VerificationPurpose purpose,
            @Param("now") LocalDateTime now,
            @Param("maximumAttempts") int maximumAttempts);

    @Update("""
            UPDATE sys_email_verify_code
               SET status = 'USED', used_time = #{usedTime}, update_time = #{usedTime}
             WHERE id = #{id}
               AND status = 'UNUSED'
               AND expire_time > #{usedTime}
               AND attempt_count = #{expectedAttemptCount}
            """)
    int consume(
            @Param("id") long id,
            @Param("expectedAttemptCount") int expectedAttemptCount,
            @Param("usedTime") LocalDateTime usedTime);

    @Update("""
            UPDATE sys_email_verify_code
               SET attempt_count = attempt_count + 1,
                   status = CASE WHEN attempt_count + 1 >= #{maximumAttempts}
                                 THEN 'INVALID' ELSE status END,
                   update_time = #{updateTime}
             WHERE id = #{id}
               AND status = 'UNUSED'
               AND expire_time > #{updateTime}
               AND attempt_count = #{expectedAttemptCount}
            """)
    int recordFailedAttempt(
            @Param("id") long id,
            @Param("expectedAttemptCount") int expectedAttemptCount,
            @Param("maximumAttempts") int maximumAttempts,
            @Param("updateTime") LocalDateTime updateTime);

    @Update("""
            UPDATE sys_email_verify_code
               SET status = 'INVALID', update_time = #{updateTime}
             WHERE id = #{id}
               AND status = 'UNUSED'
            """)
    int invalidate(@Param("id") long id, @Param("updateTime") LocalDateTime updateTime);
}
