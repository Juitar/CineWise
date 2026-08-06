package com.miaoyu.ticket.profile.infrastructure.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import com.miaoyu.ticket.profile.domain.ProfileBehaviorEventType;
import com.miaoyu.ticket.profile.domain.ProfileBehaviorTargetType;
import java.time.LocalDateTime;
import org.junit.jupiter.api.Test;

class MybatisProfileBehaviorEventRepositoryTest {
  @Test
  void shouldRestoreChangedResultMappedFromJsonBooleanColumn() {
    ProfileBehaviorEventPersistenceMapper mapper = new ProfileBehaviorEventPersistenceMapper() {
      @Override
      public ProfileBehaviorEventRow findByEventId(String eventId) {
        return new ProfileBehaviorEventRow(
            eventId, 7L, "FAVORITE", "MOVIE", "movie-1", LocalDateTime.now(), true);
      }
      @Override
      public boolean existsInWindow(
          long userId, String type, String targetType, String targetId, LocalDateTime since) {
        return false;
      }
      @Override
      public int insert(
          com.miaoyu.ticket.profile.application.ProfileBehaviorEventRepository.NewEvent event) {
        return 1;
      }
      @Override
      public int cleanupBefore(LocalDateTime before, int limit) {
        return 0;
      }
    };

    var event = new MybatisProfileBehaviorEventRepository(mapper).findByEventId("event-1").orElseThrow();

    assertThat(event.changed()).isTrue();
    assertThat(event.eventType()).isEqualTo(ProfileBehaviorEventType.FAVORITE);
    assertThat(event.targetType()).isEqualTo(ProfileBehaviorTargetType.MOVIE);
  }
}
