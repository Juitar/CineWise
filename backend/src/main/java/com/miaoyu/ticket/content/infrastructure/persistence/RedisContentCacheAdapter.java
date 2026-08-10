package com.miaoyu.ticket.content.infrastructure.persistence;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.miaoyu.ticket.common.config.ClockConfiguration;
import com.miaoyu.ticket.content.application.ContentCachePort;
import com.miaoyu.ticket.content.application.ContentProperties;
import com.miaoyu.ticket.content.application.ContentQuery;
import com.miaoyu.ticket.content.application.ContentResult;
import com.miaoyu.ticket.content.domain.ContentFallbackType;
import com.miaoyu.ticket.content.domain.ContentItem;
import java.time.Clock;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Repository;

/**
 * Redis 缓存适配器；连接失败按未命中处理，不能阻断后续快照和 Demo 回退。
 *
 * <p>Redis 仅是加速层，绝不能决定内容是否存在。网络故障、序列化失败和损坏缓存都回到 Application
 * Service，让它继续读取持久化快照或 Demo。</p>
 *
 * <p>缓存保存的是 ContentResult 的标准 JSON，不保存第三方原始响应、当前用户、精确位置或票务数据。
 * 这样缓存被清理或升级时不会影响订单和场次的真实状态。</p>
 *
 * <p>TTL 由配置提供，不写死在代码中；即使 TTL 尚未到期，也会检查内容 expiresAt，防止配置变更或
 * 旧条目导致过期内容被当作有效缓存返回。</p>
 */
@Repository
public class RedisContentCacheAdapter implements ContentCachePort {

    private static final long FAILURE_COOLDOWN_NANOS = Duration.ofSeconds(30).toNanos();

    private final StringRedisTemplate redisTemplate;
    private final ContentResultCodec codec;
    private final ContentProperties properties;
    private final Clock clock;
    /** Redis 只是加速层；连续故障期间直接走 MySQL 快照，避免每次页面请求重复等待连接超时。 */
    private volatile long redisDisabledUntilNanos;

    /** Clock 与查询服务共用，防止缓存边界在不同服务器时区下判断不一致。 */
    /** Codec 只处理标准内容，Redis 不需要了解影片和影院的业务字段。 */
    public RedisContentCacheAdapter(StringRedisTemplate redisTemplate, ObjectMapper objectMapper,
                                    ContentProperties properties, Clock clock) {
        this.redisTemplate = redisTemplate;
        this.codec = new ContentResultCodec(objectMapper);
        this.properties = properties;
        this.clock = clock;
    }

    /**
     * 缓存条目自身过期或内容有效期已到都按未命中处理，不能返回旧缓存冒充有效数据。
     *
     * <p>读取异常不写日志正文，避免缓存键中的查询信息在错误日志中扩散。</p>
     */
    @Override
    public Optional<ContentResult<List<? extends ContentItem>>> find(ContentQuery query) {
        if (System.nanoTime() < redisDisabledUntilNanos) {
            return Optional.empty();
        }
        try {
            String payload = redisTemplate.opsForValue().get(ContentCacheKeyFactory.create(query));
            if (payload == null) {
                return Optional.empty();
            }
            ContentResult<List<? extends ContentItem>> result = codec.read(
                    payload, query, ContentFallbackType.CACHE, false);
            LocalDateTime now = LocalDateTime.ofInstant(clock.instant(), ClockConfiguration.BUSINESS_ZONE_ID);
            if (result.expiresAt().isBefore(now)) {
                return Optional.empty();
            }
            // Redis 只是当前真实资料的读取位置，不能把命中缓存误报为降级。
            ContentResult<List<? extends ContentItem>> current = new ContentResult<>(
                    result.data(), result.source(), result.dataTime(), result.expiresAt(), false, false, null);
            return Optional.of(current);
        } catch (DataAccessException | IllegalStateException exception) {
            // 包括连接断开和损坏 JSON，统一让调用方继续走快照。
            redisDisabledUntilNanos = System.nanoTime() + FAILURE_COOLDOWN_NANOS;
            return Optional.empty();
        }
    }

    /**
     * 缓存只写标准化 JSON；不可用时忽略写失败，让主查询仍可返回已有数据。
     *
     * <p>不做写入重试，以免缓存故障占用用户查询线程；下次成功查询会自然补回缓存。</p>
     */
    @Override
    public void save(ContentQuery query, ContentResult<List<? extends ContentItem>> result) {
        if (System.nanoTime() < redisDisabledUntilNanos) {
            return;
        }
        // 由配置控制 TTL，部署环境无需重新编译即可调整缓存占用。
        try {
            redisTemplate.opsForValue().set(
                    ContentCacheKeyFactory.create(query), codec.write(result), properties.cacheTtl());
        } catch (DataAccessException | IllegalStateException ignored) {
            // Redis 是可选加速层，写失败不可改变内容查询结果。
            redisDisabledUntilNanos = System.nanoTime() + FAILURE_COOLDOWN_NANOS;
        }
    }
}
