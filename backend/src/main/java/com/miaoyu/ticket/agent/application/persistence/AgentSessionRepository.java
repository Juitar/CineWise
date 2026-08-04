package com.miaoyu.ticket.agent.application.persistence;

import com.miaoyu.ticket.agent.domain.persistence.AgentSession;
import java.time.LocalDateTime;
import java.util.Optional;

/** B 自有会话存储端口；所有读取和条件更新均绑定内部用户 ID。 */
public interface AgentSessionRepository {

    Optional<AgentSession> findBySessionIdAndUserId(String sessionId, long userId);

    void insert(AgentSession session);

    /** 仅在会话尚未占用活动运行时写入内部 run ID，并将会话到期时间延长到本次运行到期时间。 */
    boolean claimActiveRun(long sessionId, long userId, long runId, LocalDateTime runExpireAt);

    /** 仅在会话仍指向当前内部 run ID 时释放，避免旧运行清除新运行。 */
    boolean releaseActiveRun(long sessionId, long runId);
}
