package com.miaoyu.ticket.content.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.OffsetDateTime;
import java.util.List;
import org.junit.jupiter.api.Test;

class ContentPurchaseQueryPortTest {
    private static final OffsetDateTime DATA_AT = OffsetDateTime.parse("2026-08-07T08:00:00+08:00");
    private static final OffsetDateTime EXPIRES_AT = OffsetDateTime.parse("2026-08-07T09:00:00+08:00");

    @Test
    void givenUnsortedDuplicateReferences_whenConstructingCatalog_thenItDeduplicatesAndSortsByLocalId() {
        var movie2 = new ContentPurchaseQueryPort.MovieRef(2L, "movie-2", 100);
        var movie1 = new ContentPurchaseQueryPort.MovieRef(1L, "movie-1", 90);
        var cinema2 = new ContentPurchaseQueryPort.CinemaRef(2L, "cinema-2");
        var cinema1 = new ContentPurchaseQueryPort.CinemaRef(1L, "cinema-1");

        var catalog = new ContentPurchaseQueryPort.DemoPurchaseCatalog(
                List.of(movie2, movie1, movie2), List.of(cinema2, cinema1, cinema2),
                "NETSTART_MAOYAN", DATA_AT, EXPIRES_AT);

        assertThat(catalog.movies()).extracting(ref -> ref.movieId()).containsExactly(1L, 2L);
        assertThat(catalog.cinemas()).extracting(ref -> ref.cinemaId()).containsExactly(1L, 2L);
    }

    @Test
    void givenConflictingOrNullReferences_whenConstructingCatalog_thenItRejectsThem() {
        var firstMovie = new ContentPurchaseQueryPort.MovieRef(1L, "movie-1", 90);
        var conflictingMovie = new ContentPurchaseQueryPort.MovieRef(1L, "movie-other", 90);

        assertThatThrownBy(() -> new ContentPurchaseQueryPort.DemoPurchaseCatalog(
                List.of(firstMovie, conflictingMovie), List.of(), "NETSTART_MAOYAN", DATA_AT, EXPIRES_AT))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new ContentPurchaseQueryPort.DemoPurchaseCatalog(
                null, List.of(), "NETSTART_MAOYAN", DATA_AT, EXPIRES_AT))
                .isInstanceOf(NullPointerException.class);
    }
}
