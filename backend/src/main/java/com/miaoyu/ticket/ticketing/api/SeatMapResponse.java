package com.miaoyu.ticket.ticketing.api;

import java.time.OffsetDateTime;
import java.util.List;

/** 已冻结的最新权威座位图 REST 响应契约。 */
public record SeatMapResponse(
        String showId,
        String auditoriumId,
        String auditoriumName,
        int rowCount,
        int seatCount,
        int availableSeatCount,
        int stateVersion,
        OffsetDateTime updatedAt,
        List<SeatItemResponse> seats) {

    public SeatMapResponse {
        seats = List.copyOf(seats);
    }

    public record SeatItemResponse(
            String seatId,
            String rowNo,
            String seatNo,
            String seatLabel,
            String status,
            int stateVersion) {
    }
}
