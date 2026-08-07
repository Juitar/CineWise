package com.miaoyu.ticket.auth.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import com.miaoyu.ticket.common.error.BusinessException;
import com.miaoyu.ticket.profile.application.ProfileDataConsentSnapshot;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

class ProfileDataConsentServiceTest {
    private static final Instant NOW = Instant.parse("2026-08-07T00:00:00Z");
    private final ConsentRepository consentRepository = new ConsentRepository();
    private final OutboxRepository outboxRepository = new OutboxRepository();
    private final ProfileDataConsentOutboxDeliveryService deliveryService =
            mock(ProfileDataConsentOutboxDeliveryService.class);
    private final ProfileDataConsentService service = new ProfileDataConsentService(
            consentRepository,
            outboxRepository,
            deliveryService,
            () -> 9001L,
            Clock.fixed(NOW, ZoneOffset.UTC));

    @AfterEach
    void clearTransactionSynchronization() {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.clearSynchronization();
        }
    }

    @Test
    void shouldReturnFixedSnapshotWhenNoConsentRecordExists() {
        ProfileDataConsentSnapshot snapshot = service.findByUserId(1001L);

        assertThat(snapshot).isEqualTo(ProfileDataConsentSnapshot.notGranted());
    }

    @Test
    void shouldCreateFirstConsentAndKeepRepeatedGrantIdempotent() {
        ProfileDataConsentSnapshot first = service.grant(1001L, " 2026-08 ");
        ProfileDataConsentSnapshot repeated = service.grant(1001L, "2026-08");

        assertThat(first.granted()).isTrue();
        assertThat(first.consentVersion()).isEqualTo(1L);
        assertThat(first.version()).isZero();
        assertThat(repeated).isEqualTo(first);
        assertThat(consentRepository.insertCount).isEqualTo(1);
    }

    @Test
    void shouldRegrantWithdrawnConsentAndIncrementBothVersions() {
        consentRepository.record = new ProfileDataConsentRepository.ConsentRecord(
                1001L,
                ProfileDataConsentRepository.Status.WITHDRAWN,
                2L,
                4L,
                NOW.minusSeconds(60),
                NOW.minusSeconds(30));

        ProfileDataConsentSnapshot result = service.grant(1001L, "2026-08");

        assertThat(result.granted()).isTrue();
        assertThat(result.consentVersion()).isEqualTo(3L);
        assertThat(result.version()).isEqualTo(5L);
        assertThat(result.withdrawnAt()).isNull();
    }

    @Test
    void shouldWithdrawWithOneOutboxAndDeliverOnlyAfterCommit() {
        consentRepository.record = grantedRecord();
        TransactionSynchronizationManager.initSynchronization();

        ProfileDataConsentService.WithdrawalResult result = service.withdraw(1001L);

        assertThat(result.changed()).isTrue();
        assertThat(outboxRepository.events).singleElement().satisfies(event -> {
            assertThat(event.eventId()).isEqualTo(result.eventId());
            assertThat(event.consentVersion()).isEqualTo(2L);
            assertThat(event.consentRecordVersion()).isEqualTo(5L);
            assertThat(event.occurredAt()).isEqualTo(NOW);
        });
        for (TransactionSynchronization synchronization
                : TransactionSynchronizationManager.getSynchronizations()) {
            synchronization.afterCommit();
        }
        verify(deliveryService).deliverPending(result.eventId());
    }

    @Test
    void shouldIgnoreRepeatedWithdrawalWithoutCreatingAnotherEvent() {
        consentRepository.record = new ProfileDataConsentRepository.ConsentRecord(
                1001L,
                ProfileDataConsentRepository.Status.WITHDRAWN,
                2L,
                5L,
                NOW.minusSeconds(60),
                NOW);

        ProfileDataConsentService.WithdrawalResult result = service.withdraw(1001L);

        assertThat(result.changed()).isFalse();
        assertThat(result.eventId()).isNull();
        assertThat(outboxRepository.events).isEmpty();
    }

    @Test
    void shouldRejectCasConflictBeforeWritingOutbox() {
        consentRepository.record = grantedRecord();
        consentRepository.casSucceeds = false;

        assertThatThrownBy(() -> service.withdraw(1001L))
                .isInstanceOfSatisfying(BusinessException.class, exception ->
                        assertThat(exception.getErrorCode())
                                .isEqualTo(AuthErrorCode.PROFILE_DATA_CONSENT_CONFLICT));
        assertThat(outboxRepository.events).isEmpty();
    }

    private ProfileDataConsentRepository.ConsentRecord grantedRecord() {
        return new ProfileDataConsentRepository.ConsentRecord(
                1001L,
                ProfileDataConsentRepository.Status.GRANTED,
                2L,
                4L,
                NOW.minusSeconds(60),
                null);
    }

    private static final class ConsentRepository implements ProfileDataConsentRepository {
        private ConsentRecord record;
        private int insertCount;
        private boolean casSucceeds = true;

        @Override
        public Optional<ConsentRecord> findByUserId(long userId) {
            return Optional.ofNullable(record);
        }

        @Override
        public void insertGranted(long id, long userId, String privacyPolicyVersion, Instant now) {
            insertCount++;
            record = new ConsentRecord(userId, Status.GRANTED, 1L, 0L, now, null);
        }

        @Override
        public boolean regrant(
                long userId, long expectedRecordVersion, String privacyPolicyVersion, Instant now) {
            if (!casSucceeds || record == null || record.recordVersion() != expectedRecordVersion) {
                return false;
            }
            record = new ConsentRecord(
                    userId,
                    Status.GRANTED,
                    record.consentVersion() + 1,
                    record.recordVersion() + 1,
                    now,
                    null);
            return true;
        }

        @Override
        public boolean withdraw(long userId, long expectedRecordVersion, Instant now) {
            if (!casSucceeds || record == null || record.recordVersion() != expectedRecordVersion) {
                return false;
            }
            record = new ConsentRecord(
                    userId,
                    Status.WITHDRAWN,
                    record.consentVersion(),
                    record.recordVersion() + 1,
                    record.grantedAt(),
                    now);
            return true;
        }
    }

    private static final class OutboxRepository implements ProfileDataConsentOutboxRepository {
        private final List<OutboxEvent> events = new ArrayList<>();

        @Override
        public void insert(OutboxEvent event) {
            events.add(event);
        }

        @Override
        public Optional<OutboxEvent> findPendingByEventId(String eventId) {
            return Optional.empty();
        }

        @Override
        public Optional<OutboxEvent> findExhaustedByEventId(String eventId) {
            return Optional.empty();
        }

        @Override
        public List<OutboxEvent> findReady(Instant now, int limit) {
            return List.of();
        }

        @Override
        public boolean markDelivered(String eventId, Instant deliveredAt, Status expectedStatus) {
            return false;
        }

        @Override
        public boolean recordAutomaticFailure(
                String eventId, int expectedRetryCount, Instant nextAttemptAt, Instant now) {
            return false;
        }

        @Override
        public boolean markExhausted(String eventId, int expectedRetryCount, Instant now) {
            return false;
        }
    }
}
