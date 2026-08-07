package com.miaoyu.ticket.ticketing.application;

import com.miaoyu.ticket.common.config.ClockConfiguration;
import com.miaoyu.ticket.common.id.BusinessIdGenerator;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 将一条已准入的计划原子落为 A 本地沙箱影厅、场次、座位和 V019 映射。 */
@Service
public class ExternalShowtimeSandboxPersistenceService {

    private static final String SANDBOX_PREFIX = "本地沙箱·";

    private final ExternalShowtimeSandboxImportRepository repository;
    private final ExternalShowtimeSandboxImportProperties properties;
    private final BusinessIdGenerator idGenerator;
    private final Clock clock;

    public ExternalShowtimeSandboxPersistenceService(ExternalShowtimeSandboxImportRepository repository,
            ExternalShowtimeSandboxImportProperties properties, BusinessIdGenerator idGenerator, Clock clock) {
        this.repository = repository;
        this.properties = properties;
        this.idGenerator = idGenerator;
        this.clock = clock;
    }

    /** 外部调用已在事务外完成；重复候选直接返回首次创建的本地场次。 */
    @Transactional
    public long importEntry(ExternalShowtimeSandboxImportPlan.Entry entry) {
        ExternalShowtimeSandboxReference reference = entry.reference();
        return repository.findMappedShowId(
                reference.provider(), reference.externalCinemaId(), reference.externalShowId())
                .orElseGet(() -> create(reference, entry.estimatedEndTime()));
    }

    private long create(ExternalShowtimeSandboxReference reference, LocalDateTime estimatedEndTime) {
        LocalDateTime now = LocalDateTime.ofInstant(clock.instant(), ClockConfiguration.BUSINESS_ZONE_ID);
        int seatCount = Math.multiplyExact(properties.rowCount(), properties.seatsPerRow());
        long showId = idGenerator.nextId();
        // 先占用外部唯一键。事务回滚会同时撤销映射与本地事实，竞争方则在该键上收敛为既有 showId。
        try {
            repository.insertMapping(new ExternalShowtimeSandboxImportRepository.MappingRow(
                    idGenerator.nextId(), reference.provider(), reference.externalCinemaId(),
                    reference.externalShowId(), showId, "SANDBOX_REFERENCE",
                    LocalDateTime.ofInstant(reference.dataAt().toInstant(), ClockConfiguration.BUSINESS_ZONE_ID),
                    LocalDateTime.ofInstant(
                            reference.expiresAt().toInstant(), ClockConfiguration.BUSINESS_ZONE_ID), now));
        } catch (DuplicateKeyException duplicate) {
            return repository.findMappedShowIdAfterConflict(
                    reference.provider(), reference.externalCinemaId(), reference.externalShowId())
                    .orElseThrow(() -> duplicate);
        }
        long auditoriumId = repository.ensureSandboxAuditorium(
                new ExternalShowtimeSandboxImportRepository.AuditoriumRow(
                idGenerator.nextId(), reference.cinemaId(), auditoriumName(reference.auditoriumText()),
                properties.rowCount(), seatCount, now));
        repository.insertSandboxShow(new ExternalShowtimeSandboxImportRepository.ShowRow(showId, reference.movieId(),
                reference.cinemaId(), auditoriumId,
                LocalDateTime.ofInstant(reference.startTime().toInstant(), ClockConfiguration.BUSINESS_ZONE_ID),
                estimatedEndTime, properties.languageVersion(), properties.basePrice(), now));
        repository.insertSeats(seats(showId, now));
        return showId;
    }

    private List<ExternalShowtimeSandboxImportRepository.SeatRow> seats(long showId, LocalDateTime now) {
        List<ExternalShowtimeSandboxImportRepository.SeatRow> rows = new ArrayList<>();
        for (int row = 0; row < properties.rowCount(); row++) {
            String rowNo = Character.toString('A' + row);
            for (int seat = 1; seat <= properties.seatsPerRow(); seat++) {
                rows.add(new ExternalShowtimeSandboxImportRepository.SeatRow(idGenerator.nextId(), showId, rowNo,
                        String.format(Locale.ROOT, "%02d", seat), rowNo + "排" + seat + "座", now));
            }
        }
        return rows;
    }

    private static String auditoriumName(String auditoriumText) {
        String reference = auditoriumText == null || auditoriumText.isBlank() ? "外部参考厅" : auditoriumText.trim();
        return SANDBOX_PREFIX + reference;
    }
}
