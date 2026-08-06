package com.miaoyu.ticket.profile.application;

import static org.assertj.core.api.Assertions.assertThat;

import com.miaoyu.ticket.profile.domain.ProfileTagPolarity;
import com.miaoyu.ticket.profile.domain.ProfileTagSource;
import com.miaoyu.ticket.profile.domain.ProfileTagStatus;
import com.miaoyu.ticket.profile.domain.ProfileTagType;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

/** 固定时钟验证衰减窗口，避免测试随机器时区和当前日期波动。 */
class ProfileDecayJobTest {
  private static final Instant NOW = Instant.parse("2026-08-05T00:00:00Z");

  @Test
  void shouldDecayThirtyDayOldBehaviorTagWithVersionCondition() {
    RecordingRepository repository = new RecordingRepository(snapshot(31, 59, "0.800"));
    RecordingPreferenceRepository preferences = new RecordingPreferenceRepository();
    RecordingCache cache = new RecordingCache();
    ProfileDecayJob job =
        new ProfileDecayJob(repository, preferences, cache, Clock.fixed(NOW, ZoneOffset.UTC));

    assertThat(job.executeOnce()).isEqualTo(1);
    assertThat(repository.updatedWeight).isEqualByComparingTo("0.680");
    assertThat(repository.updatedStatus).isEqualTo(ProfileTagStatus.ACTIVE);
    assertThat(repository.expectedVersion).isEqualTo(7L);
    assertThat(preferences.incrementedUserIds).containsExactly(12L);
    assertThat(cache.invalidatedUserIds).containsExactly(12L);
  }

  @Test
  void shouldExpireAtOriginalBehaviorDeadlineAfterPriorDecayUpdatedTime() {
    // 已在第 60 天衰减过，所以 update_time 只有 30 天；仍必须在原行为的第 90 天过期。
    RecordingRepository repository = new RecordingRepository(snapshot(30, 0, "0.680"));
    RecordingPreferenceRepository preferences = new RecordingPreferenceRepository();
    RecordingCache cache = new RecordingCache();
    ProfileDecayJob job =
        new ProfileDecayJob(repository, preferences, cache, Clock.fixed(NOW, ZoneOffset.UTC));

    assertThat(job.executeOnce()).isEqualTo(1);
    assertThat(repository.updatedStatus).isEqualTo(ProfileTagStatus.EXPIRED);
    assertThat(cache.invalidatedUserIds).containsExactly(12L);
    assertThat(preferences.incrementedUserIds).containsExactly(12L);
  }

  @Test
  void shouldKeepCacheWhenVersionConditionRejectsStaleDecayTask() {
    RecordingRepository repository = new RecordingRepository(snapshot(31, 59, "0.800"));
    repository.updateResult = false;
    RecordingPreferenceRepository preferences = new RecordingPreferenceRepository();
    RecordingCache cache = new RecordingCache();
    ProfileDecayJob job =
        new ProfileDecayJob(repository, preferences, cache, Clock.fixed(NOW, ZoneOffset.UTC));

    assertThat(job.executeOnce()).isZero();
    assertThat(cache.invalidatedUserIds).isEmpty();
    assertThat(preferences.incrementedUserIds).isEmpty();
  }

  private static ProfileTagRepository.Snapshot snapshot(
      int daysSinceLastDecay, int daysUntilExpiry, String weight) {
    return new ProfileTagRepository.Snapshot(
        11L,
        12L,
        ProfileTagType.MOVIE_GENRE,
        "科幻",
        ProfileTagPolarity.LIKE,
        new BigDecimal(weight),
        ProfileTagSource.BEHAVIOR,
        new BigDecimal("0.800"),
        ProfileTagStatus.ACTIVE,
        LocalDateTime.ofInstant(NOW.plusSeconds(daysUntilExpiry * 86400L), ZoneOffset.UTC),
        7L,
        LocalDateTime.ofInstant(NOW.minusSeconds(daysSinceLastDecay * 86400L), ZoneOffset.UTC));
  }

  private static final class RecordingRepository implements ProfileTagRepository {
    private final Snapshot dueTag;
    private BigDecimal updatedWeight;
    private ProfileTagStatus updatedStatus;
    private long expectedVersion;
    private boolean updateResult = true;

    private RecordingRepository(Snapshot dueTag) {
      this.dueTag = dueTag;
    }

    @Override
    public void insert(NewTag tag) {
      throw new UnsupportedOperationException("衰减任务不创建标签");
    }

    @Override
    public Optional<Snapshot> findByIdAndUserId(long tagId, long userId) {
      return Optional.empty();
    }

    @Override
    public boolean updateStatus(
        long tagId,
        long userId,
        long expected,
        ProfileTagStatus status,
        LocalDateTime updatedAt) {
      return false;
    }

    @Override
    public List<Snapshot> findPageByUserId(long userId, int offset, int limit) {
      return List.of();
    }

    @Override
    public boolean softDelete(long tagId, long userId, long expected, LocalDateTime deletedAt) {
      return false;
    }

    @Override
    public boolean updateBehaviorState(
        long tagId,
        long expected,
        BigDecimal weight,
        ProfileTagStatus status,
        LocalDateTime expiresAt,
        LocalDateTime updatedAt) {
      updatedWeight = weight;
      updatedStatus = status;
      expectedVersion = expected;
      return updateResult;
    }

    @Override
    public List<Snapshot> findBehaviorTagsDueBefore(LocalDateTime before, int limit) {
      return List.of(dueTag);
    }
  }

  private static final class RecordingCache implements ProfileSummaryCache {
    private final java.util.ArrayList<Long> invalidatedUserIds = new java.util.ArrayList<>();

    @Override
    public Optional<ProfileSummary> find(long userId, long version) {
      return Optional.empty();
    }

    @Override
    public void put(long userId, long version, ProfileSummary summary) {
      // 本测试只验证衰减后的失效调用，不需要模拟缓存写入。
    }

    @Override
    public void invalidateUser(long userId) {
      invalidatedUserIds.add(userId);
    }
  }

  private static final class RecordingPreferenceRepository implements ProfilePreferenceRepository {
    private final java.util.ArrayList<Long> incrementedUserIds = new java.util.ArrayList<>();

    @Override
    public Optional<Snapshot> findByUserId(long userId) {
      return Optional.empty();
    }

    @Override
    public Optional<Snapshot> findByUserIdForUpdate(long userId) {
      return Optional.empty();
    }

    @Override
    public void insertDefault(long userId, LocalDateTime now) {
      throw new UnsupportedOperationException("衰减任务不创建默认设置");
    }

    @Override
    public boolean incrementVersion(long userId, LocalDateTime updatedAt) {
      incrementedUserIds.add(userId);
      return true;
    }
  }
}
