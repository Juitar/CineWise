package com.miaoyu.ticket.profile.application;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.miaoyu.ticket.auth.application.CurrentUser;
import com.miaoyu.ticket.auth.application.CurrentUserAccessor;
import com.miaoyu.ticket.auth.application.RoleCode;
import com.miaoyu.ticket.common.id.BusinessIdGenerator;
import com.miaoyu.ticket.profile.domain.ProfileTagPolarity;
import com.miaoyu.ticket.profile.domain.ProfileTagSource;
import com.miaoyu.ticket.profile.domain.ProfileTagStatus;
import com.miaoyu.ticket.profile.domain.ProfileTagType;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

/** 验证幂等记录保存真实 JSON，重放时必须先于版本校验返回首次结果。 */
class ProfileManagementServiceTest {
  @Test
  void shouldReplayFirstTagResponseBeforeCheckingAdvancedPreferenceVersion() {
    PreferenceRepository preferences = new PreferenceRepository();
    TagRepository tags = new TagRepository();
    WriteRepository writes = new WriteRepository();
    ProfileManagementService service = new ProfileManagementService(
        currentUserAccessor(),
        userId -> new ProfileDataConsentSnapshot(true, 1L, 0L, null, null),
        preferences,
        tags,
        writes,
        new EmptyCache(),
        ids(),
        Clock.fixed(Instant.parse("2026-08-06T00:00:00Z"), ZoneOffset.UTC),
        new ObjectMapper().findAndRegisterModules());
    ProfileManagementService.CreateTagCommand command = new ProfileManagementService.CreateTagCommand(
        0, "create-1", ProfileTagType.MOVIE_GENRE, "科幻|太空", ProfileTagPolarity.LIKE,
        new BigDecimal("0.900"));

    ProfileManagementService.TagView first = service.createMyManualTag(command);
    preferences.snapshot = new ProfilePreferenceRepository.Snapshot(7L, true, 9L, LocalDateTime.now());
    ProfileManagementService.TagView replay = service.createMyManualTag(command);

    assertThat(replay).isEqualTo(first);
    assertThat(tags.inserted).hasSize(1);
    assertThat(writes.rows.getFirst().responseJson()).contains("科幻|太空");
  }

  private static CurrentUserAccessor currentUserAccessor() {
    return () -> new CurrentUser(7L, RoleCode.USER, 1L);
  }

  private static BusinessIdGenerator ids() {
    return new BusinessIdGenerator() {
      private long id = 100L;
      @Override
      public long nextId() { return id++; }
    };
  }

  private static final class PreferenceRepository implements ProfilePreferenceRepository {
    private Snapshot snapshot = new Snapshot(7L, true, 0L, LocalDateTime.now());
    @Override public Optional<Snapshot> findByUserId(long userId) { return Optional.of(snapshot); }
    @Override public Optional<Snapshot> findByUserIdForUpdate(long userId) { return Optional.of(snapshot); }
    @Override public void insertDefault(long userId, LocalDateTime now) { }
    @Override public boolean incrementVersion(long userId, LocalDateTime updatedAt) { return true; }
    @Override
    public boolean incrementVersionIfMatches(long userId, long expectedVersion, LocalDateTime updatedAt) {
      return true;
    }
  }

  private static final class TagRepository implements ProfileTagRepository {
    private final List<NewTag> inserted = new ArrayList<>();
    @Override public void insert(NewTag tag) { inserted.add(tag); }
    @Override public Optional<Snapshot> findByIdAndUserId(long tagId, long userId) { return Optional.empty(); }
    @Override public boolean updateStatus(long a, long b, long c, ProfileTagStatus d, LocalDateTime e) { return false; }
    @Override public List<Snapshot> findPageByUserId(long userId, int offset, int limit) { return List.of(); }
    @Override public boolean softDelete(long a, long b, long c, LocalDateTime d) { return false; }
    @Override
    public boolean updateBehaviorState(
        long a,
        long b,
        BigDecimal c,
        ProfileTagStatus d,
        LocalDateTime e,
        LocalDateTime f) {
      return false;
    }
    @Override public List<Snapshot> findBehaviorTagsDueBefore(LocalDateTime before, int limit) { return List.of(); }
  }

  private static final class WriteRepository implements ProfileWriteRequestRepository {
    private final List<NewRequest> rows = new ArrayList<>();
    @Override
    public Optional<Snapshot> findByUserIdAndOperationAndIdempotencyKey(
        long userId, String operation, String key) {
      return rows.stream().filter(row -> row.userId() == userId && row.operation().equals(operation)
          && row.idempotencyKey().equals(key)).findFirst().map(row -> new Snapshot(row.userId(), row.operation(),
          row.idempotencyKey(), row.requestHash(), row.httpStatus(), row.responseJson(), row.expiresAt()));
    }
    @Override public void insert(NewRequest request) { rows.add(request); }
  }

  private static final class EmptyCache implements ProfileSummaryCache {
    @Override public Optional<ProfileSummary> find(long userId, long version) { return Optional.empty(); }
    @Override public void put(long userId, long version, ProfileSummary summary) { }
    @Override public void invalidateUser(long userId) { }
  }
}
