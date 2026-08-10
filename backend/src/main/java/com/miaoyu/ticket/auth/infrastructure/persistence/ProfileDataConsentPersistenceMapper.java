package com.miaoyu.ticket.auth.infrastructure.persistence;

import java.time.LocalDateTime;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

/** C 私有同意表 SQL；其他模块不得依赖本 Mapper。 */
@Mapper
public interface ProfileDataConsentPersistenceMapper {

  @Select("""
      SELECT user_id, status, consent_version, version, granted_at, withdrawn_at
        FROM sys_profile_data_consent
       WHERE user_id = #{userId}
      """)
  Row findByUserId(@Param("userId") long userId);

  @Select("""
      SELECT user_id, status, consent_version, version, granted_at, withdrawn_at
        FROM sys_profile_data_consent
       WHERE user_id = #{userId}
       FOR UPDATE
      """)
  Row findByUserIdForUpdate(@Param("userId") long userId);

  @Insert("""
      INSERT INTO sys_profile_data_consent (
          id, user_id, status, consent_version, privacy_policy_version,
          granted_at, withdrawn_at, version, create_time, update_time
      ) VALUES (
          #{id}, #{userId}, 'GRANTED', 1, #{privacyPolicyVersion},
          #{now}, NULL, 0, #{now}, #{now}
      )
      """)
  int insertGranted(
      @Param("id") long id,
      @Param("userId") long userId,
      @Param("privacyPolicyVersion") String privacyPolicyVersion,
      @Param("now") LocalDateTime now);

  @Update("""
      UPDATE sys_profile_data_consent
         SET status = 'GRANTED',
             consent_version = consent_version + 1,
             privacy_policy_version = #{privacyPolicyVersion},
             granted_at = #{now},
             withdrawn_at = NULL,
             version = version + 1,
             update_time = #{now}
       WHERE user_id = #{userId}
         AND status = 'WITHDRAWN'
         AND version = #{expectedRecordVersion}
      """)
  int regrant(
      @Param("userId") long userId,
      @Param("expectedRecordVersion") long expectedRecordVersion,
      @Param("privacyPolicyVersion") String privacyPolicyVersion,
      @Param("now") LocalDateTime now);

  @Update("""
      UPDATE sys_profile_data_consent
         SET status = 'WITHDRAWN',
             withdrawn_at = #{now},
             version = version + 1,
             update_time = #{now}
       WHERE user_id = #{userId}
         AND status = 'GRANTED'
         AND version = #{expectedRecordVersion}
      """)
  int withdraw(
      @Param("userId") long userId,
      @Param("expectedRecordVersion") long expectedRecordVersion,
      @Param("now") LocalDateTime now);

  record Row(
      long userId,
      String status,
      long consentVersion,
      long version,
      LocalDateTime grantedAt,
      LocalDateTime withdrawnAt) { }
}
