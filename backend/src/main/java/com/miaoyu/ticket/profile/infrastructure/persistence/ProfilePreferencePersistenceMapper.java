package com.miaoyu.ticket.profile.infrastructure.persistence;

import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

/** user_preference 最小 SQL；同意校验和并发规则必须留在应用服务。 */
@Mapper
public interface ProfilePreferencePersistenceMapper {
  @Select(
      """
      SELECT user_id AS user_id, personalization_enabled AS personalization_enabled,
             version, update_time AS updated_at
        FROM user_preference WHERE user_id = #{userId}
      """)
  ProfilePreferenceRow findByUserId(@Param("userId") long userId);

  /** 行锁只服务于同一用户的首次创建和行为归一化短事务，不能扩大为跨用户锁。 */
  @Select(
      """
      SELECT user_id AS user_id, personalization_enabled AS personalization_enabled,
             version, update_time AS updated_at
        FROM user_preference WHERE user_id = #{userId} FOR UPDATE
      """)
  ProfilePreferenceRow findByUserIdForUpdate(@Param("userId") long userId);

  @Insert(
      """
      INSERT INTO user_preference (
          user_id, personalization_enabled, version, create_time, update_time, deleted_at
      ) VALUES (#{userId}, TRUE, 0, #{now}, #{now}, NULL)
      """)
  int insertDefault(@Param("userId") long userId, @Param("now") java.time.LocalDateTime now);
}
