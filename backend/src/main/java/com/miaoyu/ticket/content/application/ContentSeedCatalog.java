package com.miaoyu.ticket.content.application;

import java.util.List;

/** 内容模块提供给票务种子编排器的稳定标识集合。 */
public record ContentSeedCatalog(List<MovieRef> movies, List<CinemaRef> cinemas) {

    public ContentSeedCatalog {
        movies = List.copyOf(movies);
        cinemas = List.copyOf(cinemas);
    }

    public record MovieRef(long id, String sourceMovieId, int durationMinutes) {
    }

    public record CinemaRef(long id, String sourceCinemaId) {
    }
}
