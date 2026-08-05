package com.miaoyu.ticket.agent.application.persistence;

import com.miaoyu.ticket.agent.domain.persistence.AgentSession;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/** B 自有会话存储端口；所有读取和条件更新均绑定内部用户 ID。 */
public interface AgentSessionRepository {

    Optional<AgentSession> findBySessionIdAndUserId(String sessionId, long userId);

    Optional<AgentSession> findBySessionIdAndUserIdForUpdate(String sessionId, long userId);

    Optional<AgentSession> findByIdAndUserId(long id, long userId);

    List<AgentSession> findActiveByUserId(long userId, int offset, int limit);

    long countActiveByUserId(long userId);

    List<AgentSession> findAllActiveByUserId(long userId);

    void insert(AgentSession session);

    /** 仅在会话尚未占用活动运行时写入内部 run ID，并将会话到期时间延长到本次运行到期时间。 */
    boolean claimActiveRun(long sessionId, long userId, long runId, LocalDateTime runExpireAt);

    /** 仅在会话仍指向当前内部 run ID 时释放，避免旧运行清除新运行。 */
    boolean releaseActiveRun(long sessionId, long runId);

    /** 仅清空本人仍处于 ACTIVE 且没有活动运行的会话，避免读后写覆盖并发提交。 */
    boolean clearIfActiveAndInactive(long sessionId, long userId, LocalDateTime now);

    /** 逻辑清空后提前到期其 B 自有子记录，仍由既有清理任务按顺序物理删除。 */
    void expireSessionData(long sessionId, String externalSessionId, LocalDateTime now);

    boolean deleteIfEmptyAndInactive(long sessionId);
}
