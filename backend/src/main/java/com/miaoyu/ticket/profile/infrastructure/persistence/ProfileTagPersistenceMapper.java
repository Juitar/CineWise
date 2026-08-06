package com.miaoyu.ticket.profile.infrastructure.persistence;

import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

/** 标签 SQL 限定本人 userId，避免仅凭 tagId 修改其他用户画像。 */
@Mapper
public interface ProfileTagPersistenceMapper {
  @Insert(
      """
INSERT INTO user_profile_tag (id, user_id, tag_type, tag_value, polarity, weight, source, confidence,
    status, expires_at, version, deleted_at, create_time, update_time)
VALUES (#{tag.id}, #{tag.userId}, #{tag.type}, #{tag.value}, #{tag.polarity}, #{tag.weight},
    #{tag.source}, #{tag.confidence}, #{tag.status}, #{tag.expiresAt}, 0, NULL,
    #{tag.createdAt}, #{tag.createdAt})
""")
  int insert(@Param("tag") com.miaoyu.ticket.profile.application.ProfileTagRepository.NewTag tag);

  @Select(
      """
SELECT id, user_id AS user_id, tag_type AS tag_type, tag_value AS tag_value, polarity,
       weight, source, confidence, status, expires_at AS expires_at, version, update_time AS updated_at
  FROM user_profile_tag WHERE id = #{tagId} AND user_id = #{userId} AND deleted_at IS NULL
""")
  ProfileTagRow findByIdAndUserId(@Param("tagId") long tagId, @Param("userId") long userId);

  @Select(
      """
SELECT id, user_id AS user_id, tag_type AS tag_type, tag_value AS tag_value, polarity,
       weight, source, confidence, status, expires_at AS expires_at, version, update_time AS updated_at
  FROM user_profile_tag
 WHERE user_id = #{userId} AND tag_type = #{type} AND tag_value = #{value}
   AND source = #{source} AND status = 'ACTIVE' AND deleted_at IS NULL
""")
  ProfileTagRow findActiveByKey(
      @Param("userId") long userId,
      @Param("type") String type,
      @Param("value") String value,
      @Param("source") String source);

  @Update(
      """
UPDATE user_profile_tag
   SET weight = #{weight}, polarity = #{polarity}, expires_at = #{expiresAt},
       version = version + 1, update_time = #{updatedAt}
 WHERE id = #{tagId} AND source = 'BEHAVIOR' AND status = 'ACTIVE'
   AND version = #{expectedVersion} AND deleted_at IS NULL
""")
  int updateBehaviorWeight(
      @Param("tagId") long tagId,
      @Param("expectedVersion") long expectedVersion,
      @Param("weight") java.math.BigDecimal weight,
      @Param("polarity") String polarity,
      @Param("expiresAt") java.time.LocalDateTime expiresAt,
      @Param("updatedAt") java.time.LocalDateTime updatedAt);

  @Update(
      """
UPDATE user_profile_tag SET status = #{status}, version = version + 1, update_time = #{updatedAt}
 WHERE id = #{tagId} AND user_id = #{userId} AND version = #{expectedVersion} AND deleted_at IS NULL
""")
  int updateStatus(
      @Param("tagId") long tagId,
      @Param("userId") long userId,
      @Param("expectedVersion") long expectedVersion,
      @Param("status") String status,
      @Param("updatedAt") java.time.LocalDateTime updatedAt);

  @Update(
      """
UPDATE user_profile_tag
   SET polarity = #{polarity}, weight = #{weight}, confidence = #{confidence},
       expires_at = #{expiresAt}, version = version + 1, update_time = #{updatedAt}
 WHERE id = #{tagId} AND user_id = #{userId} AND version = #{expectedVersion}
   AND status = 'ACTIVE' AND deleted_at IS NULL
""")
  int update(
      @Param("tagId") long tagId,
      @Param("userId") long userId,
      @Param("expectedVersion") long expectedVersion,
      @Param("polarity") String polarity,
      @Param("weight") java.math.BigDecimal weight,
      @Param("confidence") java.math.BigDecimal confidence,
      @Param("expiresAt") java.time.LocalDateTime expiresAt,
      @Param("updatedAt") java.time.LocalDateTime updatedAt);

  @Select(
      """
SELECT id, user_id AS user_id, tag_type AS tag_type, tag_value AS tag_value, polarity,
       weight, source, confidence, status, expires_at AS expires_at, version, update_time AS updated_at
  FROM user_profile_tag WHERE user_id = #{userId} AND deleted_at IS NULL
 ORDER BY update_time DESC, id DESC LIMIT #{limit} OFFSET #{offset}
""")
  java.util.List<ProfileTagRow> findPageByUserId(
      @Param("userId") long userId, @Param("offset") int offset, @Param("limit") int limit);

  @Select("SELECT COUNT(*) FROM user_profile_tag WHERE user_id = #{userId} AND deleted_at IS NULL")
  long countByUserId(@Param("userId") long userId);

  @Update(
      """
UPDATE user_profile_tag SET status = 'DELETED', deleted_at = #{deletedAt},
       version = version + 1, update_time = #{deletedAt}
 WHERE id = #{tagId} AND user_id = #{userId} AND version = #{expectedVersion} AND deleted_at IS NULL
""")
  int softDelete(
      @Param("tagId") long tagId,
      @Param("userId") long userId,
      @Param("expectedVersion") long expectedVersion,
      @Param("deletedAt") java.time.LocalDateTime deletedAt);

  @Update(
      """
UPDATE user_profile_tag SET status = 'DELETED', deleted_at = #{deletedAt}, update_time = #{deletedAt}
 WHERE user_id = #{userId} AND deleted_at IS NULL
""")
  int softDeleteAll(@Param("userId") long userId, @Param("deletedAt") java.time.LocalDateTime deletedAt);

  @org.apache.ibatis.annotations.Delete(
      "DELETE FROM user_profile_tag WHERE deleted_at IS NOT NULL AND deleted_at <= #{before} LIMIT #{limit}")
  int cleanupDeletedBefore(
      @Param("before") java.time.LocalDateTime before, @Param("limit") int limit);

  @Update(
      """
UPDATE user_profile_tag
   SET weight = #{weight}, status = #{status}, expires_at = #{expiresAt},
       version = version + 1, update_time = #{updatedAt}
 WHERE id = #{tagId} AND source = 'BEHAVIOR' AND status = 'ACTIVE'
   AND version = #{expectedVersion} AND deleted_at IS NULL
""")
  int updateBehaviorState(
      @Param("tagId") long tagId,
      @Param("expectedVersion") long expectedVersion,
      @Param("weight") java.math.BigDecimal weight,
      @Param("status") String status,
      @Param("expiresAt") java.time.LocalDateTime expiresAt,
      @Param("updatedAt") java.time.LocalDateTime updatedAt);

  @Select(
      """
SELECT id, user_id AS user_id, tag_type AS tag_type, tag_value AS tag_value, polarity,
       weight, source, confidence, status, expires_at AS expires_at, version, update_time AS updated_at
  FROM user_profile_tag WHERE source = 'BEHAVIOR' AND status = 'ACTIVE'
   AND update_time <= #{before} ORDER BY update_time ASC LIMIT #{limit}
""")
  java.util.List<ProfileTagRow> findBehaviorTagsDueBefore(
      @Param("before") java.time.LocalDateTime before, @Param("limit") int limit);
}
