package com.miaoyu.ticket.auth.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.miaoyu.ticket.auth.domain.RegistrationInvite;
import com.miaoyu.ticket.auth.domain.RegistrationInviteStatus;
import com.miaoyu.ticket.common.id.BusinessIdGenerator;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class InitialRegistrationInviteServiceTest {

    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-08-05T02:00:00Z"), ZoneOffset.UTC);
    private static final String HASH = "a".repeat(64);

    private final RegistrationInviteRepository repository = mock(RegistrationInviteRepository.class);
    private final RegistrationInviteHasher hasher = mock(RegistrationInviteHasher.class);
    private final BusinessIdGenerator idGenerator = mock(BusinessIdGenerator.class);
    private InitialRegistrationInviteService service;

    @BeforeEach
    void setUp() {
        service = new InitialRegistrationInviteService(repository, hasher, idGenerator, CLOCK);
    }

    @Test
    void shouldCreateTheOnlyConfiguredInviteWhenMissing() {
        InitialRegistrationInviteCommand command = command();
        when(hasher.hash("private-invite")).thenReturn(HASH);
        when(repository.findByCodeHash(HASH)).thenReturn(Optional.empty());
        when(idGenerator.nextId()).thenReturn(123L);
        when(repository.createIfAbsent(
                        org.mockito.ArgumentMatchers.any(RegistrationInvite.class),
                        org.mockito.ArgumentMatchers.eq(now())))
                .thenReturn(true);

        assertThat(service.createIfMissing(command)).isTrue();

        ArgumentCaptor<RegistrationInvite> captor = ArgumentCaptor.forClass(RegistrationInvite.class);
        verify(repository).createIfAbsent(captor.capture(), org.mockito.ArgumentMatchers.eq(now()));
        RegistrationInvite invite = captor.getValue();
        assertThat(invite.id()).isEqualTo(123L);
        assertThat(invite.codeHash()).isEqualTo(HASH);
        assertThat(invite.status()).isEqualTo(RegistrationInviteStatus.ENABLED);
        assertThat(invite.maxUses()).isEqualTo(100);
        assertThat(invite.usedCount()).isZero();
        assertThat(invite.version()).isZero();
    }

    @Test
    void shouldLeaveExistingInviteCompletelyUnchanged() {
        RegistrationInvite existing = new RegistrationInvite(
                99L,
                HASH,
                RegistrationInviteStatus.DISABLED,
                100,
                42,
                LocalDateTime.parse("2026-08-01T00:00:00"),
                LocalDateTime.parse("2026-12-31T23:59:59.999"),
                7);
        when(hasher.hash("private-invite")).thenReturn(HASH);
        when(repository.findByCodeHash(HASH)).thenReturn(Optional.of(existing));

        assertThat(service.createIfMissing(command())).isFalse();

        verify(idGenerator, never()).nextId();
        verify(repository, never()).createIfAbsent(
                org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any());
    }

    @Test
    void shouldRejectExpiredOrMalformedPrivateConfiguration() {
        assertThatThrownBy(() -> service.createIfMissing(new InitialRegistrationInviteCommand(
                        "private-invite", 100, "invalid", "2026-12-31T23:59:59.999")))
                .isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> service.createIfMissing(new InitialRegistrationInviteCommand(
                        "private-invite", 100, "2026-08-01T00:00:00", "2026-08-04T00:00:00")))
                .isInstanceOf(IllegalStateException.class);

        verify(hasher, never()).hash(org.mockito.ArgumentMatchers.anyString());
    }

    @Test
    void shouldRedactInviteCodeFromCommandText() {
        assertThat(command().toString()).doesNotContain("private-invite").contains("REDACTED");
    }

    private InitialRegistrationInviteCommand command() {
        return new InitialRegistrationInviteCommand(
                "private-invite", 100, "2026-08-05T00:00:00", "2026-12-31T23:59:59.999");
    }

    private LocalDateTime now() {
        return LocalDateTime.now(CLOCK);
    }
}
