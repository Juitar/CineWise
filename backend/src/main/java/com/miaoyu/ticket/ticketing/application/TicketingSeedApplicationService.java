package com.miaoyu.ticket.ticketing.application;

import com.miaoyu.ticket.common.config.ClockConfiguration;
import com.miaoyu.ticket.common.config.SeedProperties;
import com.miaoyu.ticket.common.id.BusinessIdGenerator;
import com.miaoyu.ticket.content.application.ContentSeedCatalog;
import com.miaoyu.ticket.content.application.ContentPurchaseQueryPort;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.SplittableRandom;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 幂等补齐滚动七天的演示排期；已有座位只读取、不覆盖，避免破坏锁定或售出状态。 */
@Service
public class TicketingSeedApplicationService {

    private static final int HALLS_PER_CINEMA = 2;
    private static final int ROW_COUNT = 8;
    private static final int SEATS_PER_ROW = 10;
    private static final int DAYS = 7;
    private static final List<LocalTime> SHOW_TIMES = List.of(
            LocalTime.of(9, 30),
            LocalTime.of(14, 0),
            LocalTime.of(19, 30));
    private static final List<BigDecimal> PRICES = List.of(
            new BigDecimal("39.90"),
            new BigDecimal("49.90"),
            new BigDecimal("59.90"));

    private final TicketingSeedRepository repository;
    private final BusinessIdGenerator idGenerator;
    private final SeedProperties properties;
    private final Clock clock;

    public TicketingSeedApplicationService(
            TicketingSeedRepository repository,
            BusinessIdGenerator idGenerator,
            SeedProperties properties,
            Clock clock) {
        this.repository = repository;
        this.idGenerator = idGenerator;
        this.properties = properties;
        this.clock = clock;
    }

    /**
     * 为内容种子补齐每家影院的影厅、未来七天早中晚场次和完整座位图。
     * 重复执行只插入缺失数据，不使用新的雪花 ID 改写已有业务数据。
     *
     * @param catalog 内容模块已确保存在的影片、影院标识
     * @return 本轮应达到的影厅、场次和座位规模
     */
    @Transactional
    public TicketingSeedReport ensureFixedSeed(ContentSeedCatalog catalog) {
        if (catalog.movies().isEmpty() || catalog.cinemas().isEmpty()) {
            throw new IllegalArgumentException("Fixed ticketing seed requires movies and cinemas");
        }
        return ensureSeed(catalog);
    }

    /**
     * 为 D 返回的真实内容目录补齐本地 Mock 排期。
     *
     * <p>返回的影片和影院 ID 已由 D 标准化，A 只创建自己拥有的影厅、场次、座位和价格。目录到期时
     * 不再新增任何数据，避免把过时内容继续扩展为可购演示排期。</p>
     */
    @Transactional
    public TicketingSeedReport ensureLiveDemoSeed(ContentPurchaseQueryPort.DemoPurchaseCatalog catalog) {
        if (catalog == null || catalog.movies().isEmpty() || catalog.cinemas().isEmpty()) {
            return emptyReport();
        }
        OffsetDateTime now = OffsetDateTime.ofInstant(clock.instant(), ClockConfiguration.BUSINESS_ZONE_ID);
        if (!catalog.expiresAt().isAfter(now)) {
            return emptyReport();
        }
        ContentSeedCatalog references = new ContentSeedCatalog(
                catalog.movies().stream()
                        .map(movie -> new ContentSeedCatalog.MovieRef(
                                movie.movieId(), movie.sourceMovieId(), movie.durationMinutes()))
                        .toList(),
                catalog.cinemas().stream()
                        .map(cinema -> new ContentSeedCatalog.CinemaRef(cinema.cinemaId(), cinema.sourceCinemaId()))
                        .toList());
        return ensureSeed(references);
    }

    private TicketingSeedReport ensureSeed(ContentSeedCatalog catalog) {

        LocalDateTime generatedAt = LocalDateTime.ofInstant(clock.instant(), ClockConfiguration.BUSINESS_ZONE_ID);
        LocalDate runDate = LocalDate.now(clock);
        int auditoriumCount = 0;
        int showCount = 0;
        int seatCount = 0;

        for (ContentSeedCatalog.CinemaRef cinema : catalog.cinemas()) {
            for (int hallNumber = 1; hallNumber <= HALLS_PER_CINEMA; hallNumber++) {
                long auditoriumId = ensureAuditorium(cinema.id(), hallNumber, generatedAt);
                auditoriumCount++;
                for (int dayOffset = 0; dayOffset < DAYS; dayOffset++) {
                    LocalDate showDate = runDate.plusDays(dayOffset);
                    if (repository.hasExternalShowForDate(cinema.id(), showDate)) {
                        continue;
                    }
                    for (int slotIndex = 0; slotIndex < SHOW_TIMES.size(); slotIndex++) {
                        LocalDateTime startTime = showDate.atTime(SHOW_TIMES.get(slotIndex));
                        ContentSeedCatalog.MovieRef movie = selectMovie(
                                catalog.movies(), cinema.sourceCinemaId(), hallNumber, dayOffset, slotIndex);
                        long showId = ensureShow(
                                movie,
                                cinema.id(),
                                auditoriumId,
                                startTime,
                                generatedAt,
                                cinema.sourceCinemaId(),
                                hallNumber,
                                dayOffset,
                                slotIndex);
                        showCount++;
                        seatCount += ensureSeatMap(showId, generatedAt);
                    }
                }
            }
        }
        return new TicketingSeedReport(auditoriumCount, showCount, seatCount);
    }

    private TicketingSeedReport emptyReport() {
        return new TicketingSeedReport(0, 0, 0);
    }

    private long ensureAuditorium(long cinemaId, int hallNumber, LocalDateTime generatedAt) {
        return repository.ensureAuditorium(new TicketingSeedRepository.AuditoriumSeed(
                idGenerator.nextId(),
                cinemaId,
                hallNumber + "号厅",
                ROW_COUNT,
                ROW_COUNT * SEATS_PER_ROW,
                generatedAt));
    }

    private long ensureShow(
            ContentSeedCatalog.MovieRef movie,
            long cinemaId,
            long auditoriumId,
            LocalDateTime startTime,
            LocalDateTime generatedAt,
            String sourceCinemaId,
            int hallNumber,
            int dayOffset,
            int slotIndex) {
        SplittableRandom random = randomFor(sourceCinemaId, hallNumber, dayOffset, slotIndex);
        BigDecimal price = PRICES.get(random.nextInt(PRICES.size()));
        return repository.ensureShow(new TicketingSeedRepository.ShowSeed(
                idGenerator.nextId(),
                movie.id(),
                cinemaId,
                auditoriumId,
                startTime,
                startTime.plusMinutes(movie.durationMinutes()),
                "国语 2D",
                price,
                generatedAt));
    }

    private int ensureSeatMap(long showId, LocalDateTime generatedAt) {
        // 先读取自然业务键，只补缺失座位；绝不更新已有座位的锁定、售出或版本字段。
        Set<String> existingSeatKeys = repository.findSeatKeys(showId);
        List<TicketingSeedRepository.SeatSeed> missingSeats = new ArrayList<>();
        for (int rowIndex = 0; rowIndex < ROW_COUNT; rowIndex++) {
            String rowNo = Character.toString('A' + rowIndex);
            for (int seatIndex = 1; seatIndex <= SEATS_PER_ROW; seatIndex++) {
                String seatNo = String.format(Locale.ROOT, "%02d", seatIndex);
                TicketingSeedRepository.SeatSeed seat = new TicketingSeedRepository.SeatSeed(
                        idGenerator.nextId(),
                        showId,
                        rowNo,
                        seatNo,
                        rowNo + "排" + seatIndex + "座",
                        generatedAt);
                if (!existingSeatKeys.contains(seat.businessKey())) {
                    missingSeats.add(seat);
                }
            }
        }
        repository.insertSeats(missingSeats);
        return ROW_COUNT * SEATS_PER_ROW;
    }

    private ContentSeedCatalog.MovieRef selectMovie(
            List<ContentSeedCatalog.MovieRef> movies,
            String sourceCinemaId,
            int hallNumber,
            int dayOffset,
            int slotIndex) {
        SplittableRandom random = randomFor(sourceCinemaId, hallNumber, dayOffset, slotIndex);
        return movies.get(random.nextInt(movies.size()));
    }

    private SplittableRandom randomFor(String sourceCinemaId, int hallNumber, int dayOffset, int slotIndex) {
        // 随机性只依赖固定种子和稳定业务键，不能依赖每次运行都会变化的雪花 ID。
        long mixedSeed = properties.fixedValue()
                ^ sourceCinemaId.hashCode()
                ^ ((long) hallNumber << 48)
                ^ ((long) dayOffset << 24)
                ^ slotIndex;
        return new SplittableRandom(mixedSeed);
    }
}
