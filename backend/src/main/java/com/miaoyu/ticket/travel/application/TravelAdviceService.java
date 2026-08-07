package com.miaoyu.ticket.travel.application;

import com.miaoyu.ticket.common.config.ClockConfiguration;
import com.miaoyu.ticket.common.id.BusinessIdGenerator;
import com.miaoyu.ticket.travel.domain.TravelTaskStatus;
import java.time.Clock;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.util.Objects;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 根据天气和固定规则生成建议快照。
 *
 * <p>天气只是建议的一个来源，天气服务全部不可用时仍保留通用交通提示并让任务进入 READY；这样不会
 * 因第三方能力失败影响电子票或已经成功创建的出行任务。</p>
 */
@Service
public class TravelAdviceService {

    private static final int GENERATION_LOCK_COUNT = 64;

    private final TravelTaskRepository taskRepository;
    private final TravelAdviceRepository adviceRepository;
    private final WeatherQueryService weatherQueryService;
    private final BusinessIdGenerator idGenerator;
    private final Clock clock;
    private final Object[] generationLocks = new Object[GENERATION_LOCK_COUNT];

    public TravelAdviceService(
            TravelTaskRepository taskRepository,
            TravelAdviceRepository adviceRepository,
            WeatherQueryService weatherQueryService,
            BusinessIdGenerator idGenerator,
            Clock clock) {
        this.taskRepository = taskRepository;
        this.adviceRepository = adviceRepository;
        this.weatherQueryService = weatherQueryService;
        this.idGenerator = idGenerator;
        this.clock = clock;
        for (int index = 0; index < GENERATION_LOCK_COUNT; index++) {
            generationLocks[index] = new Object();
        }
    }

    /**
     * 为指定任务追加一个新版本建议。
     *
     * <p>先用数据库条件更新抢占版本，再查询天气。这样同一任务版本只有赢家会调用外部 Provider；
     * 输家直接读取赢家写入的快照，不能在高并发时额外消耗天气配额。</p>
     */
    @Transactional
    public TravelAdviceSnapshot generate(long taskId) {
        synchronized (generationLocks[Math.floorMod(Long.hashCode(taskId), GENERATION_LOCK_COUNT)]) {
            return generateUnderTaskLock(taskId);
        }
    }

    /**
     * 同一 JVM 先按任务串行，避免输家在数据库版本竞争前访问天气来源。
     *
     * <p>多实例仍由 {@code claimVersionForAdvice} 的数据库条件更新裁决，因此本地锁只是减少重复外部
     * 调用，不能替代版本和唯一约束。</p>
     */
    private TravelAdviceSnapshot generateUnderTaskLock(long taskId) {
        TravelTaskRepository.TravelTaskSnapshot task = taskRepository.findById(taskId)
                .orElseThrow(() -> new IllegalArgumentException("出行任务不存在"));
        if (task.status().isTerminal()) {
            throw new IllegalStateException("已结束的出行任务不能生成建议");
        }
        LocalDateTime now = currentBusinessTime();
        if (!adviceRepository.claimVersionForAdvice(task.id(), task.version(), now)) {
            return adviceRepository.findCommittedByTaskIdAndVersion(task.id(), task.version() + 1)
                    .orElseThrow(() -> new IllegalStateException("建议版本抢占失败后未找到已生成快照"));
        }
        // 新任务必须按影院 ID 查静态坐标，再由天气服务逆地理取得 adcode。
        // V013 前的历史任务允许没有 cinemaId，只能保留已登记区域映射的明确回退，不能猜测坐标。
        WeatherObservation weather = task.cinemaId() == null
                ? weatherQueryService.query(task.cinemaArea())
                : weatherQueryService.query(task.cinemaId());

        TravelAdviceSnapshot snapshot = new TravelAdviceSnapshot(
                idGenerator.nextId(), task.id(), task.version() + 1, weatherJson(weather), adviceJson(weather),
                weather.source(), toLocal(weather.dataTime()), toLocal(weather.expiresAt()), weather.isExpired(),
                weather.degraded(), weather.fallbackType(), now);
        // 与版本条件更新同一事务；插入异常会回滚 READY 和版本递增，确保下次可安全恢复。
        adviceRepository.insert(snapshot);
        return snapshot;
    }

    private String adviceJson(WeatherObservation weather) {
        String transportAdvice = "请预留充足时间，优先选择公共交通并提前到场";
        String weatherAdvice = weather.condition() == null
                ? "天气暂不可用，请出发前自行确认"
                : Objects.requireNonNullElse(weather.risk(), "请关注出发前天气变化");
        return "{\"weatherAdvice\":\"" + escape(weatherAdvice)
                + "\",\"transportAdvice\":\"" + escape(transportAdvice) + "\"}";
    }

    private String weatherJson(WeatherObservation weather) {
        if (weather.condition() == null) {
            return null;
        }
        return "{\"area\":\"" + escape(weather.area()) + "\",\"condition\":\""
                + escape(weather.condition()) + "\",\"risk\":\"" + escape(weather.risk()) + "\"}";
    }

    private String escape(String value) {
        return Objects.requireNonNullElse(value, "").replace("\\", "\\\\").replace("\"", "\\\"");
    }

    private LocalDateTime currentBusinessTime() {
        return LocalDateTime.ofInstant(clock.instant(), ClockConfiguration.BUSINESS_ZONE_ID);
    }

    private LocalDateTime toLocal(OffsetDateTime time) {
        return time.atZoneSameInstant(ClockConfiguration.BUSINESS_ZONE_ID).toLocalDateTime();
    }
}
