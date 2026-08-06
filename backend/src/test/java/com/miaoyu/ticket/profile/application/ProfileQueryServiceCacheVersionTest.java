package com.miaoyu.ticket.profile.application;

import static org.assertj.core.api.Assertions.assertThat;

import com.miaoyu.ticket.profile.domain.ProfileTagStatus;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

/** 验证缓存删除失败时，已推进的持久化版本仍会让查询绕过旧摘要键。 */
class ProfileQueryServiceCacheVersionTest {
  @Test
  void shouldNotReadOldSummaryKeyAfterPreferenceVersionAdvances() {
    VersionFourPreferenceRepository preferences = new VersionFourPreferenceRepository();
    RecordingCache cache = new RecordingCache();
    ProfileQueryService service =
        new ProfileQueryService(
            preferences,
            new EmptyTagRepository(),
            new ProfileSummaryAssembler(Clock.fixed(Instant.EPOCH, ZoneOffset.UTC)),
            cache);

    ProfileSummary summary = service.assembleSummary(12L);

    assertThat(summary.version()).isEqualTo(4L);
    assertThat(cache.readVersions).containsExactly(4L);
    assertThat(cache.writtenVersions).containsExactly(4L);
  }

  private static final class VersionFourPreferenceRepository implements ProfilePreferenceRepository {
    @Override
    public Optional<Snapshot> findByUserId(long userId) {
      return Optional.of(new Snapshot(userId, false, 4L, LocalDateTime.of(2026, 8, 6, 0, 0)));
    }

    @Override
    public Optional<Snapshot> findByUserIdForUpdate(long userId) {
      return findByUserId(userId);
    }

    @Override
    public void insertDefault(long userId, LocalDateTime now) {
      throw new UnsupportedOperationException("本测试只验证已有画像的缓存版本读取");
    }

    @Override
    public boolean incrementVersion(long userId, LocalDateTime updatedAt) {
      return true;
    }
  }

  private static final class EmptyTagRepository implements ProfileTagRepository {
    @Override
    public void insert(NewTag tag) {
      throw new UnsupportedOperationException("查询不会创建标签");
    }

    @Override
    public Optional<Snapshot> findByIdAndUserId(long tagId, long userId) {
      return Optional.empty();
    }

    @Override
    public boolean updateStatus(
        long tagId,
        long userId,
        long expectedVersion,
        ProfileTagStatus status,
        LocalDateTime updatedAt) {
      return false;
    }

    @Override
    public List<Snapshot> findPageByUserId(long userId, int offset, int limit) {
      return List.of();
    }

    @Override
    public boolean softDelete(long tagId, long userId, long expectedVersion, LocalDateTime deletedAt) {
      return false;
    }

    @Override
    public boolean updateBehaviorState(
        long tagId,
        long expectedVersion,
        java.math.BigDecimal weight,
        ProfileTagStatus status,
        LocalDateTime expiresAt,
        LocalDateTime updatedAt) {
      return false;
    }

    @Override
    public List<Snapshot> findBehaviorTagsDueBefore(LocalDateTime before, int limit) {
      return List.of();
    }
  }

  private static final class RecordingCache implements ProfileSummaryCache {
    private final List<Long> readVersions = new ArrayList<>();
    private final List<Long> writtenVersions = new ArrayList<>();

    @Override
    public Optional<ProfileSummary> find(long userId, long version) {
      readVersions.add(version);
      return Optional.empty();
    }

    @Override
    public void put(long userId, long version, ProfileSummary summary) {
      writtenVersions.add(version);
    }

    @Override
    public void invalidateUser(long userId) {
      // 模拟 Redis 删除暂时失败；版本键仍保证读取不会碰到旧摘要。
    }
  }
}
