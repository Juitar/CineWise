package com.miaoyu.ticket.ticketing.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.miaoyu.ticket.common.id.BusinessIdGenerator;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class ExternalShowtimeSandboxPersistenceServiceTest {

    @Test
    void givenNewSandboxReference_whenImported_thenCreatesOnlyLocalSandboxFacts() {
        ExternalShowtimeSandboxImportRepository repository = mock(ExternalShowtimeSandboxImportRepository.class);
        when(repository.findMappedShowId("NETSTART", "cinema-1", "show-1"))
                .thenReturn(Optional.empty());
        ExternalShowtimeSandboxPersistenceService service = service(repository);

        long showId = service.importEntry(entry());

        assertThat(showId).isEqualTo(101L);
        ArgumentCaptor<ExternalShowtimeSandboxImportRepository.AuditoriumRow> auditorium =
                ArgumentCaptor.forClass(ExternalShowtimeSandboxImportRepository.AuditoriumRow.class);
        ArgumentCaptor<ExternalShowtimeSandboxImportRepository.ShowRow> show =
                ArgumentCaptor.forClass(ExternalShowtimeSandboxImportRepository.ShowRow.class);
        ArgumentCaptor<ExternalShowtimeSandboxImportRepository.MappingRow> mapping =
                ArgumentCaptor.forClass(ExternalShowtimeSandboxImportRepository.MappingRow.class);
        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<ExternalShowtimeSandboxImportRepository.SeatRow>> seats =
                ArgumentCaptor.forClass(List.class);
        verify(repository).ensureSandboxAuditorium(auditorium.capture());
        verify(repository).insertSandboxShow(show.capture());
        verify(repository).insertSeats(seats.capture());
        verify(repository).insertMapping(mapping.capture());
        assertThat(auditorium.getValue().name()).startsWith("本地沙箱·");
        assertThat(show.getValue().basePrice()).isEqualByComparingTo("39.90");
        assertThat(show.getValue().endTime()).isEqualTo(LocalDateTime.of(2026, 8, 7, 11, 30));
        assertThat(mapping.getValue().showId()).isEqualTo(showId);
        assertThat(mapping.getValue().source()).isEqualTo("SANDBOX_REFERENCE");
        assertThat(seats.getValue()).hasSize(80)
                .allSatisfy(seat -> assertThat(seat.showId()).isEqualTo(showId));
    }

    @Test
    void givenExistingMapping_whenImportedAgain_thenDoesNotCreateLocalFactsAgain() {
        ExternalShowtimeSandboxImportRepository repository = mock(ExternalShowtimeSandboxImportRepository.class);
        when(repository.findMappedShowId("NETSTART", "cinema-1", "show-1"))
                .thenReturn(Optional.of(901L));

        long showId = service(repository).importEntry(entry());

        assertThat(showId).isEqualTo(901L);
        verify(repository, times(0)).ensureSandboxAuditorium(any());
        verify(repository, times(0)).insertSandboxShow(any());
        verify(repository, times(0)).insertSeats(any());
        verify(repository, times(0)).insertMapping(any());
    }

    private static ExternalShowtimeSandboxPersistenceService service(
            ExternalShowtimeSandboxImportRepository repository) {
        BusinessIdGenerator idGenerator = new BusinessIdGenerator() {
            private long next = 100;

            @Override
            public long nextId() {
                return ++next;
            }
        };
        return new ExternalShowtimeSandboxPersistenceService(repository,
                new ExternalShowtimeSandboxImportProperties(new BigDecimal("39.90"), 8, 10, "国语 2D"),
                idGenerator, Clock.fixed(Instant.parse("2026-08-07T01:00:00Z"), ZoneId.of("Asia/Shanghai")));
    }

    private static ExternalShowtimeSandboxImportPlan.Entry entry() {
        return new ExternalShowtimeSandboxImportPlan.Entry(new ExternalShowtimeSandboxReference(
                "NETSTART", "cinema-1", "show-1", 11L, 22L,
                OffsetDateTime.parse("2026-08-07T10:00:00+08:00"), 90, "1号厅",
                OffsetDateTime.parse("2026-08-07T09:00:00+08:00"),
                OffsetDateTime.parse("2026-08-07T10:00:00+08:00"), true, false, false, false),
                LocalDateTime.of(2026, 8, 7, 11, 30));
    }
}
