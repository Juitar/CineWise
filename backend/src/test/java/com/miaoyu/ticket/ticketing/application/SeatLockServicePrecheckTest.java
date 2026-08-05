package com.miaoyu.ticket.ticketing.application;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.miaoyu.ticket.common.error.BusinessException;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class SeatLockServicePrecheckTest {

    private static final Clock FIXED_CLOCK = Clock.fixed(
            Instant.parse("2026-08-05T02:00:00Z"),
            ZoneId.of("Asia/Shanghai"));

    @Test
    void givenAvailableShowAndSeats_whenPrecheck_thenReadOnlySucceedsWithoutLock() {
        SeatLockRepository repository = mock(SeatLockRepository.class);
        when(repository.findShow(70001L)).thenReturn(Optional.of(show(
                "ON_SALE", LocalDateTime.of(2026, 8, 5, 11, 0))));
        when(repository.findSeats(eq(70001L), eq(List.of(80001L, 80002L))))
                .thenReturn(List.of(seat(80001L, "AVAILABLE"), seat(80002L, "AVAILABLE")));
        SeatLockService service = new SeatLockService(repository, FIXED_CLOCK);

        service.precheckSeats(70001L, List.of(80001L, 80002L));

        verify(repository, never()).lockSeat(org.mockito.ArgumentMatchers.any());
    }

    @Test
    void givenUnavailableSeat_whenPrecheck_thenRejectWithoutLock() {
        SeatLockRepository repository = mock(SeatLockRepository.class);
        when(repository.findShow(70001L)).thenReturn(Optional.of(show(
                "ON_SALE", LocalDateTime.of(2026, 8, 5, 11, 0))));
        when(repository.findSeats(eq(70001L), anyList()))
                .thenReturn(List.of(seat(80001L, "LOCKED")));
        SeatLockService service = new SeatLockService(repository, FIXED_CLOCK);

        assertThatThrownBy(() -> service.precheckSeats(70001L, List.of(80001L, 80002L)))
                .isInstanceOfSatisfying(BusinessException.class, exception ->
                        org.assertj.core.api.Assertions.assertThat(exception.getErrorCode())
                                .isEqualTo(TicketingErrorCode.SEAT_NOT_LOCKABLE));
        verify(repository, never()).lockSeat(org.mockito.ArgumentMatchers.any());
    }

    @Test
    void givenStartedShow_whenPrecheck_thenRejectBeforeSeatRead() {
        SeatLockRepository repository = mock(SeatLockRepository.class);
        when(repository.findShow(70001L)).thenReturn(Optional.of(show(
                "ON_SALE", LocalDateTime.of(2026, 8, 5, 9, 59))));
        SeatLockService service = new SeatLockService(repository, FIXED_CLOCK);

        assertThatThrownBy(() -> service.precheckSeats(70001L, List.of(80001L)))
                .isInstanceOfSatisfying(BusinessException.class, exception ->
                        org.assertj.core.api.Assertions.assertThat(exception.getErrorCode())
                                .isEqualTo(TicketingErrorCode.SHOW_NOT_SALEABLE));
        verify(repository, never()).findSeats(eq(70001L), anyList());
        verify(repository, never()).lockSeat(org.mockito.ArgumentMatchers.any());
    }

    private SeatLockRepository.ShowForLock show(String status, LocalDateTime startTime) {
        return new SeatLockRepository.ShowForLock(
                70001L,
                new BigDecimal("39.00"),
                status,
                startTime);
    }

    private SeatLockRepository.SeatForLock seat(long seatId, String status) {
        return new SeatLockRepository.SeatForLock(seatId, "A", Long.toString(seatId), status, 1);
    }
}
