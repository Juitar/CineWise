package com.miaoyu.ticket.profile.infrastructure;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.miaoyu.ticket.profile.application.ProfileSummary;
import com.miaoyu.ticket.profile.application.ProfileSummaryCache;
import java.time.Duration;
import java.util.Optional;
import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Repository;

/** Redis 缓存只是画像摘要加速层；任何异常都不能阻断 MySQL 回退。 */
@Repository
public class RedisProfileSummaryCache implements ProfileSummaryCache {
  /**
   * 版本键已保证写后不读旧摘要；TTL 仅限制 Redis 删除持续失败时遗留键的存活时间。
   * 时长不参与任何画像规则，过期后按 MySQL 重新组装即可。
   */
  private static final Duration SUMMARY_TTL = Duration.ofMinutes(15);
  private final StringRedisTemplate redis;
  private final ObjectMapper objectMapper;

  public RedisProfileSummaryCache(StringRedisTemplate redis, ObjectMapper objectMapper) {
    this.redis = redis;
    this.objectMapper = objectMapper;
  }

  @Override
  public Optional<ProfileSummary> find(long userId, long version) {
    try {
      String value = redis.opsForValue().get(ProfileCacheKeyFactory.summaryKey(userId, version));
      return value == null
          ? Optional.empty()
          : Optional.of(objectMapper.readValue(value, ProfileSummary.class));
    } catch (DataAccessException | JsonProcessingException exception) {
      return Optional.empty();
    }
  }

  @Override
  public void put(long userId, long version, ProfileSummary summary) {
    try {
      redis
          .opsForValue()
          .set(
              ProfileCacheKeyFactory.summaryKey(userId, version),
              objectMapper.writeValueAsString(summary),
              SUMMARY_TTL);
    } catch (DataAccessException | JsonProcessingException exception) {
      // 缓存失败由后续查询回退 MySQL，不能影响画像写入事务。
    }
  }

  @Override
  public void invalidateUser(long userId) {
    try {
      redis.delete(redis.keys(ProfileCacheKeyFactory.userPattern(userId)));
    } catch (DataAccessException exception) {
      // 删除失败不影响正确性：写事务已推进持久化版本，旧键不会再被新查询命中。
    }
  }
}
