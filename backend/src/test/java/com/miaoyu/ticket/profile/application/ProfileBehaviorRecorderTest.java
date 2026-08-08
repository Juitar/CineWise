package com.miaoyu.ticket.profile.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.miaoyu.ticket.auth.application.CurrentUser;
import com.miaoyu.ticket.auth.application.CurrentUserAccessor;
import com.miaoyu.ticket.auth.application.RoleCode;
import com.miaoyu.ticket.common.id.BusinessIdGenerator;
import com.miaoyu.ticket.common.error.BusinessException;
import com.miaoyu.ticket.content.application.MovieGenreQueryPort;
import com.miaoyu.ticket.order.event.PaymentSucceededEvent;
import com.miaoyu.ticket.profile.domain.ProfileBehaviorEventType;
import com.miaoyu.ticket.profile.domain.ProfileBehaviorTargetType;
import com.miaoyu.ticket.profile.domain.ProfileTagPolarity;
import com.miaoyu.ticket.profile.domain.ProfileTagStatus;
import com.miaoyu.ticket.profile.domain.ProfileTagType;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DuplicateKeyException;

class ProfileBehaviorRecorderTest {
  private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-08-06T00:00:00Z"), ZoneOffset.UTC);

  @Test
  void shouldReplayFirstChangedResultForSameEventId() {
    EventRepository events = new EventRepository();
    ProfileBehaviorRecorder recorder = recorder(events, new TagRepository());
    ProfileBehaviorRecorder.BehaviorCommand command = new ProfileBehaviorRecorder.BehaviorCommand(
        "event-1", ProfileBehaviorEventType.FAVORITE, ProfileBehaviorTargetType.MOVIE, "movie-1",
        ProfileTagType.MOVIE_GENRE, "科幻", LocalDateTime.now(CLOCK));

    assertThat(recorder.record(command)).isEqualTo(new ProfileBehaviorRecorder.RecordResult(true, true));
    assertThat(recorder.record(command)).isEqualTo(new ProfileBehaviorRecorder.RecordResult(true, true));
    assertThat(events.rows).hasSize(1);
  }

  @Test
  void shouldRecoverExistingResultAfterConcurrentEventIdConflict() {
    EventRepository events = new EventRepository();
    events.conflictAfterSaving = true;
    ProfileBehaviorRecorder recorder = recorder(events, new TagRepository());
    ProfileBehaviorRecorder.BehaviorCommand command = new ProfileBehaviorRecorder.BehaviorCommand(
        "event-2", ProfileBehaviorEventType.FAVORITE, ProfileBehaviorTargetType.MOVIE, "movie-2",
        ProfileTagType.MOVIE_GENRE, "科幻", LocalDateTime.now(CLOCK));

    assertThat(recorder.record(command)).isEqualTo(new ProfileBehaviorRecorder.RecordResult(true, true));
    assertThat(events.rows).hasSize(1);
  }

  @Test
  void shouldStorePaidOrderAsShowEvidenceWhenMovieGenreIsUnavailable() {
    EventRepository events = new EventRepository();
    TagRepository tags = new TagRepository();
    ProfileBehaviorRecorder recorder = recorder(events, tags);
    PaymentSucceededEvent event = new PaymentSucceededEvent("payment-1", "10", "20", "30", "40", "7", "长沙", null,
        1L, OffsetDateTime.ofInstant(CLOCK.instant(), ZoneOffset.UTC));

    assertThat(recorder.recordPayment(event)).isEqualTo(new ProfileBehaviorRecorder.RecordResult(true, false));
    assertThat(events.rows.getFirst().targetType()).isEqualTo(ProfileBehaviorTargetType.SHOW);
    assertThat(tags.inserted).isEmpty();
  }

  @Test
  void shouldCreateBehaviorGenreTagFromPaidMovieContext() {
    EventRepository events = new EventRepository();
    TagRepository tags = new TagRepository();
    ProfileBehaviorRecorder recorder = recorder(events, tags, movieId -> Optional.of("科幻"));
    PaymentSucceededEvent event = new PaymentSucceededEvent("payment-genre", "10", "20", "30", "40", "7",
        "长沙", null, 1L, OffsetDateTime.ofInstant(CLOCK.instant(), ZoneOffset.UTC));

    assertThat(recorder.recordPayment(event)).isEqualTo(new ProfileBehaviorRecorder.RecordResult(true, true));
    assertThat(events.rows).singleElement().satisfies(row -> assertThat(row.changed()).isTrue());
    assertThat(tags.inserted).singleElement().satisfies(tag -> {
      assertThat(tag.type()).isEqualTo(ProfileTagType.MOVIE_GENRE);
      assertThat(tag.value()).isEqualTo("科幻");
      assertThat(tag.source()).isEqualTo(com.miaoyu.ticket.profile.domain.ProfileTagSource.BEHAVIOR);
      assertThat(tag.weight()).isEqualByComparingTo("0.350");
    });
  }

  @Test
  void shouldRecordAcceptedPlanAsMinimalPlanEvent() {
    EventRepository events = new EventRepository();
    ProfileBehaviorRecorder recorder = recorder(events, new TagRepository());

    assertThat(recorder.recordPlanAccepted("feedback-accepted", "550e8400-e29b-41d4-a716-446655440000",
        LocalDateTime.ofInstant(CLOCK.instant(), ZoneOffset.UTC)))
        .isEqualTo(new ProfileBehaviorRecorder.RecordResult(true, false));
    assertThat(events.rows).singleElement().satisfies(row -> {
      assertThat(row.eventType()).isEqualTo(ProfileBehaviorEventType.ACCEPT_PLAN);
      assertThat(row.targetType()).isEqualTo(ProfileBehaviorTargetType.PLAN);
      assertThat(row.targetId()).isEqualTo("550e8400-e29b-41d4-a716-446655440000");
      assertThat(row.changed()).isFalse();
    });
  }

  @Test
  void shouldRecordRejectedPlanAsMinimalPlanEvent() {
    EventRepository events = new EventRepository();
    ProfileBehaviorRecorder recorder = recorder(events, new TagRepository());

    assertThat(recorder.recordPlanRejected("feedback-rejected", "550e8400-e29b-41d4-a716-446655440001",
        LocalDateTime.ofInstant(CLOCK.instant(), ZoneOffset.UTC)))
        .isEqualTo(new ProfileBehaviorRecorder.RecordResult(true, false));
    assertThat(events.rows).singleElement().satisfies(row -> {
      assertThat(row.eventType()).isEqualTo(ProfileBehaviorEventType.REJECT_PLAN);
      assertThat(row.targetType()).isEqualTo(ProfileBehaviorTargetType.PLAN);
      assertThat(row.changed()).isFalse();
    });
  }

  @Test
  void shouldReplayPlanFeedbackWithoutWritingAnotherEvent() {
    EventRepository events = new EventRepository();
    ProfileBehaviorRecorder recorder = recorder(events, new TagRepository());
    String planId = "550e8400-e29b-41d4-a716-446655440002";
    LocalDateTime occurredAt = LocalDateTime.ofInstant(CLOCK.instant(), ZoneOffset.UTC);

    assertThat(recorder.recordPlanAccepted("feedback-replay", planId, occurredAt))
        .isEqualTo(new ProfileBehaviorRecorder.RecordResult(true, false));
    assertThat(recorder.recordPlanAccepted("feedback-replay", planId, occurredAt))
        .isEqualTo(new ProfileBehaviorRecorder.RecordResult(true, false));
    assertThat(events.rows).hasSize(1);
  }

  @Test
  void shouldRejectInvalidPlanFeedbackBeforeWriting() {
    EventRepository events = new EventRepository();
    ProfileBehaviorRecorder recorder = recorder(events, new TagRepository());

    assertThatThrownBy(() -> recorder.recordPlanAccepted("feedback-invalid", "model-plan-1",
        LocalDateTime.ofInstant(CLOCK.instant(), ZoneOffset.UTC)))
        .isInstanceOf(BusinessException.class)
        .satisfies(exception -> assertThat(((BusinessException) exception).getErrorCode())
            .isEqualTo(ProfileErrorCode.INVALID_EVENT));
    assertThatThrownBy(() -> recorder.recordPlanRejected("feedback-invalid-time",
        "550e8400-e29b-41d4-a716-446655440003", null))
        .isInstanceOf(BusinessException.class)
        .satisfies(exception -> assertThat(((BusinessException) exception).getErrorCode())
            .isEqualTo(ProfileErrorCode.INVALID_EVENT));
    assertThat(events.rows).isEmpty();
  }

  private static ProfileBehaviorRecorder recorder(EventRepository events, TagRepository tags) {
    return recorder(events, tags, movieId -> Optional.empty());
  }

  private static ProfileBehaviorRecorder recorder(
      EventRepository events, TagRepository tags, MovieGenreQueryPort movieGenreQueryPort) {
    CurrentUserAccessor users = () -> new CurrentUser(7L, RoleCode.USER, 0L);
    BusinessIdGenerator ids = new BusinessIdGenerator() {
      private long id = 100L;
      @Override
      public long nextId() {
        return id++;
      }
    };
    return new ProfileBehaviorRecorder(
        users,
        userId -> new ProfileDataConsentSnapshot(true, 1L, 0L, null, null),
        new PreferenceRepository(),
        events,
        tags,
        new EmptyCache(),
        movieGenreQueryPort,
        ids,
        CLOCK);
  }

  private static final class PreferenceRepository implements ProfilePreferenceRepository {
    private final Snapshot snapshot = new Snapshot(7L, true, 0L, LocalDateTime.now(CLOCK));
    public Optional<Snapshot> findByUserId(long userId) { return Optional.of(snapshot); }
    public Optional<Snapshot> findByUserIdForUpdate(long userId) { return Optional.of(snapshot); }
    public void insertDefault(long userId, LocalDateTime now) { }
    public boolean incrementVersion(long userId, LocalDateTime updatedAt) { return true; }
    public boolean incrementVersionIfMatches(long userId, long version, LocalDateTime updatedAt) {
      return true;
    }
  }

  private static final class EventRepository implements ProfileBehaviorEventRepository {
    private final List<NewEvent> rows = new ArrayList<>();
    private boolean conflictAfterSaving;
    public Optional<Snapshot> findByEventId(String eventId) {
      return rows.stream().filter(row -> row.eventId().equals(eventId)).findFirst().map(row -> new Snapshot(
          row.eventId(), row.userId(), row.eventType(), row.targetType(), row.targetId(), row.occurredAt(),
          row.changed()));
    }
    public boolean existsInWindow(long userId, ProfileBehaviorEventType type, ProfileBehaviorTargetType targetType,
        String targetId, LocalDateTime since) { return false; }
    public void insert(NewEvent event) {
      rows.add(event);
      if (conflictAfterSaving) {
        throw new DuplicateKeyException("eventId 已被并发写入");
      }
    }
  }

  private static final class TagRepository implements ProfileTagRepository {
    private final List<NewTag> inserted = new ArrayList<>();
    public void insert(NewTag tag) { inserted.add(tag); }
    public Optional<Snapshot> findByIdAndUserId(long id, long userId) { return Optional.empty(); }
    public boolean updateStatus(long id, long userId, long version, ProfileTagStatus status, LocalDateTime now) {
      return false;
    }
    public List<Snapshot> findPageByUserId(long userId, int offset, int limit) { return List.of(); }
    public boolean softDelete(long id, long userId, long version, LocalDateTime now) { return false; }
    public boolean updateBehaviorState(long id, long version, BigDecimal weight, ProfileTagStatus status,
        LocalDateTime expiresAt, LocalDateTime now) { return false; }
    public List<Snapshot> findBehaviorTagsDueBefore(LocalDateTime before, int limit) { return List.of(); }
  }

  private static final class EmptyCache implements ProfileSummaryCache {
    public Optional<ProfileSummary> find(long userId, long version) { return Optional.empty(); }
    public void put(long userId, long version, ProfileSummary summary) { }
    public void invalidateUser(long userId) { }
  }
}
