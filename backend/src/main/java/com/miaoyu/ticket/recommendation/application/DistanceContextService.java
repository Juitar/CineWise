package com.miaoyu.ticket.recommendation.application;

import com.miaoyu.ticket.auth.application.CurrentUserAccessor;
import com.miaoyu.ticket.recommendation.domain.CinemaDistanceSelector.Coordinate;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.stereotype.Service;

/** 一次性位置上下文只在进程内短暂保存，避免精确位置进入任何持久化介质。 */
@Service
public class DistanceContextService {
    private static final long TTL_SECONDS = 300L;
    private final CurrentUserAccessor currentUserAccessor;
    private final Clock clock;
    private final Map<String, Context> contexts = new ConcurrentHashMap<>();

    public DistanceContextService(CurrentUserAccessor currentUserAccessor, Clock clock) {
        this.currentUserAccessor = currentUserAccessor;
        this.clock = clock;
    }

    /** B 使用可信 runId 创建不含位置的关联 ID，随后才允许 C 上传一次坐标。 */
    public CreatedContext create(String runId) {
        long userId = currentUserAccessor.requireCurrentUserId();
        Instant expiresAt = clock.instant().plusSeconds(TTL_SECONDS);
        String id = UUID.randomUUID().toString();
        contexts.put(id, new Context(userId, runId, expiresAt, null, false));
        return new CreatedContext(id, expiresAt);
    }

    /** C 直接上传本次坐标；重复、过期或归属不符均不暴露具体原因。 */
    public UploadResult upload(String contextId, BigDecimal longitude, BigDecimal latitude) {
        long userId = currentUserAccessor.requireCurrentUserId();
        Coordinate coordinate = new Coordinate(longitude, latitude);
        Context expected = contexts.get(contextId);
        while (expected != null) {
            // 归属、时效和一次性标记任一不满足都必须失败，并保留原上下文不被覆盖或删除。
            if (expected.userId() != userId || expected.expiresAt().isBefore(clock.instant()) || expected.used()) {
                return expected.used() ? UploadResult.CONFLICT : UploadResult.NOT_FOUND;
            }
            // replace 是基于旧对象的原子替换；并发上传只能有一个请求把空坐标替换为已上传坐标。
            Context uploaded = new Context(
                    expected.userId(), expected.runId(), expected.expiresAt(), coordinate, true);
            if (contexts.replace(contextId, expected, uploaded)) {
                return UploadResult.SUCCESS;
            }
            // 上下文可能被同一次运行中的另一个请求消费或上传，重新读取后再次按同一规则判断。
            expected = contexts.get(contextId);
        }
        return UploadResult.NOT_FOUND;
    }

    /** 推荐工具只用可信 runId 消费坐标一次；没有上下文时调用方继续普通推荐。 */
    public Coordinate consume(String contextId, String runId) {
        Context context = contexts.remove(contextId);
        if (context == null || !context.runId().equals(runId)
                || context.expiresAt().isBefore(clock.instant())) {
            return null;
        }
        return context.coordinate();
    }

    public record CreatedContext(String distanceContextId, Instant expiresAt) { }
    /** 上传结果只区分前端可以采取不同动作的状态，不泄露归属或过期的具体原因。 */
    public enum UploadResult { SUCCESS, NOT_FOUND, CONFLICT }
    private record Context(long userId, String runId, Instant expiresAt, Coordinate coordinate, boolean used) { }
}
