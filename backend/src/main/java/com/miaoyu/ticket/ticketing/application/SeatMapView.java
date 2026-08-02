package com.miaoyu.ticket.ticketing.application;

import java.time.LocalDateTime;
import java.util.List;

/** 单个可售场次的权威座位快照。 */
public record SeatMapView(
        long showId,
        long auditoriumId,
        String auditoriumName,
        int rowCount,
        int seatCount,
        int availableSeatCount,
        int stateVersion,
        LocalDateTime updatedAt,
        List<SeatItemView> seats) {

    public SeatMapView {
        seats = List.copyOf(seats);
    }

    public record SeatItemView(
            long seatId,
            String rowNo,
            String seatNo,
            String seatLabel,
            String status,
            int stateVersion) {
    }
}
