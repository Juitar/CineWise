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

    private final TravelTaskRepository taskRepository;
    private final TravelAdviceRepository adviceRepository;
    private final WeatherQueryService weatherQueryService;
    private final BusinessIdGenerator idGenerator;
    private final Clock clock;

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
    }

    /**
     * 为指定任务追加一个新版本建议。
     *
     * <p>两个并发请求都读到旧版本时，只允许其中一个条件更新成功。另一个读取成功写入的快照返回，
     * 不能覆盖旧版本或再次请求天气。</p>
     */
    @Transactional
    public TravelAdviceSnapshot generate(long taskId) {
        TravelTaskRepository.TravelTaskSnapshot task = taskRepository.findById(taskId)
                .orElseThrow(() -> new IllegalArgumentException("出行任务不存在"));
        if (task.status().isTerminal()) {
            throw new IllegalStateException("已结束的出行任务不能生成建议");
        }
        WeatherObservation weather = weatherQueryService.query(task.cinemaArea());
        LocalDateTime now = currentBusinessTime();
        if (!adviceRepository.claimVersionForAdvice(task.id(), task.version(), now)) {
            return adviceRepository.findByTaskIdAndVersion(task.id(), task.version() + 1)
                    .orElseThrow(() -> new IllegalStateException("建议版本抢占失败后未找到已生成快照"));
        }

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
