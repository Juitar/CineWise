package com.miaoyu.ticket.auth.application;

import static org.assertj.core.api.Assertions.assertThat;

import com.miaoyu.ticket.profile.application.ProfileDataConsentWithdrawnEvent;
import com.miaoyu.ticket.auth.application.ProfileDataConsentOutboxRepository.Status;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class ProfileDataConsentOutboxDeliveryServiceTest {
    private static final Instant NOW = Instant.parse("2026-08-07T00:00:00Z");

    @Test
    void shouldMarkDeliveredAfterPublishingCompleteEvent() {
        OutboxRepository repository = new OutboxRepository(event(0, Status.PENDING));
        List<ProfileDataConsentWithdrawnEvent> published = new ArrayList<>();
        ProfileDataConsentOutboxDeliveryService service = service(repository, published::add);

        assertThat(service.deliverPending("event-1")).isTrue();

        assertThat(repository.deliveredExpectedStatus).isEqualTo(Status.PENDING);
        assertThat(published).singleElement().satisfies(event -> {
            assertThat(event.eventId()).isEqualTo("event-1");
            assertThat(event.consentVersion()).isEqualTo(2L);
            assertThat(event.consentRecordVersion()).isEqualTo(5L);
            assertThat(event.occurredAt()).isEqualTo(NOW.minusSeconds(60));
        });
    }

    @Test
    void shouldScheduleFirstFailureAfterOneMinute() {
        OutboxRepository repository = new OutboxRepository(event(0, Status.PENDING));
        ProfileDataConsentOutboxDeliveryService service = service(repository, event -> {
            throw new IllegalStateException("D unavailable");
        });

        assertThat(service.deliverPending("event-1")).isFalse();

        assertThat(repository.failureExpectedRetryCount).isZero();
        assertThat(repository.nextAttemptAt).isEqualTo(NOW.plusSeconds(60));
        assertThat(repository.exhausted).isFalse();
    }

    @Test
    void shouldUseSixHoursAfterDefinedRetryDelays() {
        OutboxRepository repository = new OutboxRepository(event(5, Status.PENDING));
        ProfileDataConsentOutboxDeliveryService service = service(repository, event -> {
            throw new IllegalStateException("D unavailable");
        });

        service.deliverPending("event-1");

        assertThat(repository.nextAttemptAt).isEqualTo(NOW.plusSeconds(6 * 60 * 60));
    }

    @ParameterizedTest
    @CsvSource({
        "0,60",
        "1,300",
        "2,900",
        "3,3600",
        "4,21600"
    })
    void shouldApplyConfiguredRetrySequence(int retryCount, long delaySeconds) {
        OutboxRepository repository = new OutboxRepository(event(retryCount, Status.PENDING));
        ProfileDataConsentOutboxDeliveryService service = service(repository, event -> {
            throw new IllegalStateException("D unavailable");
        });

        service.deliverPending("event-1");

        assertThat(repository.nextAttemptAt).isEqualTo(NOW.plusSeconds(delaySeconds));
    }

    @Test
    void shouldExhaustOnTenthAutomaticFailure() {
        OutboxRepository repository = new OutboxRepository(event(9, Status.PENDING));
        ProfileDataConsentOutboxDeliveryService service = service(repository, event -> {
            throw new IllegalStateException("D unavailable");
        });

        service.deliverPending("event-1");

        assertThat(repository.exhausted).isTrue();
        assertThat(repository.failureExpectedRetryCount).isEqualTo(9);
    }

    @Test
    void shouldKeepExhaustedStateWhenManualRecoveryFails() {
        OutboxRepository repository = new OutboxRepository(event(10, Status.EXHAUSTED));
        ProfileDataConsentOutboxDeliveryService service = service(repository, event -> {
            throw new IllegalStateException("D unavailable");
        });

        assertThat(service.recoverExhausted("event-1")).isFalse();

        assertThat(repository.deliveredExpectedStatus).isNull();
        assertThat(repository.failureExpectedRetryCount).isNull();
    }

    @Test
    void shouldReuseOriginalEventWhenManualRecoverySucceeds() {
        OutboxRepository repository = new OutboxRepository(event(10, Status.EXHAUSTED));
        List<ProfileDataConsentWithdrawnEvent> published = new ArrayList<>();
        ProfileDataConsentOutboxDeliveryService service = service(repository, published::add);

        assertThat(service.recoverExhausted("event-1")).isTrue();

        assertThat(published).extracting(ProfileDataConsentWithdrawnEvent::eventId)
                .containsExactly("event-1");
        assertThat(repository.deliveredExpectedStatus).isEqualTo(Status.EXHAUSTED);
    }

    private ProfileDataConsentOutboxDeliveryService service(
            OutboxRepository repository, ProfileDataConsentWithdrawalPublisher publisher) {
        return new ProfileDataConsentOutboxDeliveryService(
                repository, publisher, Clock.fixed(NOW, ZoneOffset.UTC));
    }

    private ProfileDataConsentOutboxRepository.OutboxEvent event(int retryCount, Status status) {
        return new ProfileDataConsentOutboxRepository.OutboxEvent(
                "event-1",
                1001L,
                2L,
                5L,
                NOW.minusSeconds(60),
                "trace-1",
                status,
                retryCount,
                status == Status.PENDING ? NOW : null);
    }

    private static final class OutboxRepository implements ProfileDataConsentOutboxRepository {
        private final OutboxEvent event;
        private Status deliveredExpectedStatus;
        private Integer failureExpectedRetryCount;
        private Instant nextAttemptAt;
        private boolean exhausted;

        private OutboxRepository(OutboxEvent event) {
            this.event = event;
        }

        @Override
        public void insert(OutboxEvent event) { }

        @Override
        public Optional<OutboxEvent> findPendingByEventId(String eventId) {
            return event.status() == Status.PENDING ? Optional.of(event) : Optional.empty();
        }

        @Override
        public Optional<OutboxEvent> findExhaustedByEventId(String eventId) {
            return event.status() == Status.EXHAUSTED ? Optional.of(event) : Optional.empty();
        }

        @Override
        public List<OutboxEvent> findReady(Instant now, int limit) {
            return List.of(event);
        }

        @Override
        public boolean markDelivered(String eventId, Instant deliveredAt, Status expectedStatus) {
            deliveredExpectedStatus = expectedStatus;
            return true;
        }

        @Override
        public boolean recordAutomaticFailure(
                String eventId, int expectedRetryCount, Instant nextAttemptAt, Instant now) {
            failureExpectedRetryCount = expectedRetryCount;
            this.nextAttemptAt = nextAttemptAt;
            return true;
        }

        @Override
        public boolean markExhausted(String eventId, int expectedRetryCount, Instant now) {
            failureExpectedRetryCount = expectedRetryCount;
            exhausted = true;
            return true;
        }
    }
}
