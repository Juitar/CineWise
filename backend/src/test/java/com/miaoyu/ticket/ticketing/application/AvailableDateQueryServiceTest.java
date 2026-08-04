package com.miaoyu.ticket.ticketing.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.miaoyu.ticket.common.error.BusinessException;
import com.miaoyu.ticket.common.error.CommonErrorCode;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import org.junit.jupiter.api.Test;

class AvailableDateQueryServiceTest {

    private static final Clock FIXED_CLOCK = Clock.fixed(
            Instant.parse("2026-08-02T00:00:00Z"),
            ZoneId.of("Asia/Shanghai"));

    @Test
    void givenValidIds_whenQueryAvailableDates_thenUseBusinessClockWindowAndMapSnapshots() {
        CapturingRepository repository = new CapturingRepository(List.of(
                new AvailableDateQueryRepository.AvailableDateSnapshot(LocalDate.of(2026, 8, 2), 2),
                new AvailableDateQueryRepository.AvailableDateSnapshot(LocalDate.of(2026, 8, 3), 1)));
        AvailableDateQueryService service = new AvailableDateQueryService(repository, FIXED_CLOCK);

        List<AvailableDateView> result = service.queryAvailableDates(1001L, 2001L);

        assertThat(result).containsExactly(
                new AvailableDateView(LocalDate.of(2026, 8, 2), 2),
                new AvailableDateView(LocalDate.of(2026, 8, 3), 1));
        assertThat(repository.criteria.movieId()).isEqualTo(1001L);
        assertThat(repository.criteria.cinemaId()).isEqualTo(2001L);
        assertThat(repository.criteria.startsAfter()).isEqualTo(LocalDateTime.of(2026, 8, 2, 8, 0));
        assertThat(repository.criteria.startsBefore()).isEqualTo(LocalDateTime.of(2026, 8, 9, 0, 0));
    }

    @Test
    void givenNonPositiveId_whenQueryAvailableDates_thenRejectBeforeRepositoryCall() {
        CapturingRepository repository = new CapturingRepository(List.of());
        AvailableDateQueryService service = new AvailableDateQueryService(repository, FIXED_CLOCK);

        assertThatThrownBy(() -> service.queryAvailableDates(0L, 2001L))
                .isInstanceOfSatisfying(BusinessException.class, exception -> assertThat(exception.getErrorCode())
                        .isEqualTo(CommonErrorCode.INVALID_PARAMETER));
        assertThat(repository.criteria).isNull();
    }

    private static final class CapturingRepository implements AvailableDateQueryRepository {

        private final List<AvailableDateSnapshot> result;
        private QueryCriteria criteria;

        private CapturingRepository(List<AvailableDateSnapshot> result) {
            this.result = result;
        }

        @Override
        public List<AvailableDateSnapshot> findAvailableDates(QueryCriteria criteria) {
            this.criteria = criteria;
            return result;
        }
    }
}
