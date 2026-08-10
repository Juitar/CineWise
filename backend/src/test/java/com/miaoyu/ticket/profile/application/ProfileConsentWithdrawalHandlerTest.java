package com.miaoyu.ticket.profile.application;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class ProfileConsentWithdrawalHandlerTest {
    @Test
    void shouldDisableProfileOnceForDuplicateEventId() {
        ProfilePreferenceRepository preferences = mock(ProfilePreferenceRepository.class);
        ProfileDataConsentQuery consentQuery = mock(ProfileDataConsentQuery.class);
        ProfileTagRepository tags = mock(ProfileTagRepository.class);
        ProfileWriteRequestRepository writes = mock(ProfileWriteRequestRepository.class);
        ProfileSummaryCache cache = mock(ProfileSummaryCache.class);
        ProfileWriteRequestRepository.Snapshot replay = new ProfileWriteRequestRepository.Snapshot(
                1001L,
                "CONSENT_WITHDRAWN",
                "event-1",
                "hash",
                200,
                "{}",
                LocalDateTime.of(2026, 9, 6, 0, 0));
        when(writes.findByUserIdAndOperationAndIdempotencyKey(
                1001L, "CONSENT_WITHDRAWN", "event-1"))
                .thenReturn(Optional.empty(), Optional.of(replay));
        when(consentQuery.findByUserId(1001L)).thenReturn(
                new ProfileDataConsentSnapshot(false, 2L, 5L, null, Instant.parse("2026-08-07T00:00:00Z")));
        ProfileConsentWithdrawalHandler handler = new ProfileConsentWithdrawalHandler(
                consentQuery,
                preferences,
                tags,
                writes,
                cache,
                () -> 9001L,
                Clock.fixed(Instant.parse("2026-08-07T00:00:00Z"), ZoneOffset.UTC));
        ProfileDataConsentWithdrawnEvent event = new ProfileDataConsentWithdrawnEvent(
                "event-1",
                1001L,
                2L,
                5L,
                Instant.parse("2026-08-07T00:00:00Z"),
                "trace-1");

        handler.handle(event);
        handler.handle(event);

        verify(preferences, times(1)).disable(anyLong(), any());
        verify(tags, times(1)).softDeleteAll(anyLong(), any());
        verify(cache, times(1)).invalidateUser(1001L);
        verify(writes, times(1)).insert(any(ProfileWriteRequestRepository.NewRequest.class));
    }

    @Test
    void shouldSkipStaleWithdrawalAfterConsentWasGrantedAgain() {
        ProfileDataConsentQuery consentQuery = mock(ProfileDataConsentQuery.class);
        ProfilePreferenceRepository preferences = mock(ProfilePreferenceRepository.class);
        ProfileTagRepository tags = mock(ProfileTagRepository.class);
        ProfileWriteRequestRepository writes = mock(ProfileWriteRequestRepository.class);
        ProfileSummaryCache cache = mock(ProfileSummaryCache.class);
        when(writes.findByUserIdAndOperationAndIdempotencyKey(1001L, "CONSENT_WITHDRAWN", "event-1"))
                .thenReturn(Optional.empty());
        when(consentQuery.findByUserId(1001L)).thenReturn(
                new ProfileDataConsentSnapshot(true, 3L, 6L, Instant.parse("2026-08-07T00:00:00Z"), null));

        ProfileConsentWithdrawalHandler handler = new ProfileConsentWithdrawalHandler(
                consentQuery,
                preferences,
                tags,
                writes,
                cache,
                () -> 9001L,
                Clock.fixed(Instant.parse("2026-08-07T00:00:00Z"), ZoneOffset.UTC));

        handler.handle(new ProfileDataConsentWithdrawnEvent(
                "event-1", 1001L, 2L, 5L, Instant.parse("2026-08-07T00:00:00Z"), "trace-1"));

        verify(preferences, never()).disable(anyLong(), any());
        verify(tags, never()).softDeleteAll(anyLong(), any());
        verify(cache, never()).invalidateUser(anyLong());
        verify(writes).insert(any(ProfileWriteRequestRepository.NewRequest.class));
    }
}
