package com.miaoyu.ticket.agent.infrastructure.persistence;

import java.time.LocalDateTime;
import java.util.List;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

/** B 的四张 Agent 表 Mapper；所有 SQL 参数均使用 MyBatis 绑定，禁止拼接用户输入。 */
@Mapper
public interface AgentPersistenceMapper {
    String SESSION_COLUMNS = """
            id, session_id AS sessionId, user_id AS userId, summary, status,
            active_run_id AS activeRunId, version, create_time AS createTime,
            update_time AS updateTime, expire_at AS expireAt
            """;
    String RUN_COLUMNS = """
            id, run_id AS runId, session_id AS sessionId, user_id AS userId,
            client_request_id AS clientRequestId, request_hash_version AS requestHashVersion,
            request_hash AS requestHash, plan_id AS planId, plan_version AS planVersion, status, trace_id AS traceId,
            started_at AS startedAt, finished_at AS finishedAt, version, create_time AS createTime,
            update_time AS updateTime, expire_at AS expireAt
            """;
    String MESSAGE_COLUMNS = """
            id, message_id AS messageId, session_id AS sessionId, run_id AS runId, user_id AS userId, role,
            message_type AS messageType, text, payload_json AS payloadJson, status, completed_at AS completedAt,
            create_time AS createTime, expire_at AS expireAt
            """;
    String STEP_COLUMNS = """
            id, run_id AS runId, plan_version AS planVersion, node_id AS nodeId, node_type AS nodeType,
            depends_on_json AS dependsOnJson, input_refs_json AS inputRefsJson, status,
            failure_policy AS failurePolicy, attempt_count AS attemptCount, retry_count AS retryCount,
            recovery_pending AS recoveryPending, auto_skipped AS autoSkipped, skip_reason AS skipReason,
            skip_source_node_id AS skipSourceNodeId, slot_snapshot_json AS slotSnapshotJson,
            started_at AS startedAt, finished_at AS finishedAt, version, create_time AS createTime,
            update_time AS updateTime, expire_at AS expireAt
            """;
    String EVENT_COLUMNS = """
            event_id AS eventId, session_id AS sessionId, run_id AS runId, event_type AS eventType,
            payload_json AS payloadJson, expire_at AS expireAt, create_time AS createTime
            """;
    String EVENT_CURSOR_COLUMNS = """
            session_id AS sessionId, last_committed_event_id AS lastCommittedEventId,
            first_retained_event_id AS firstRetainedEventId, version, expire_at AS expireAt,
            create_time AS createTime, update_time AS updateTime
            """;

    @Select("SELECT " + SESSION_COLUMNS + " FROM agent_session WHERE session_id = #{sessionId}"
            + " AND user_id = #{userId} LIMIT 1")
    AgentSessionEntity findSessionBySessionIdAndUserId(
            @Param("sessionId") String sessionId, @Param("userId") long userId);

    @Select("SELECT " + SESSION_COLUMNS + " FROM agent_session WHERE session_id = #{sessionId}"
            + " AND user_id = #{userId} LIMIT 1 FOR UPDATE")
    AgentSessionEntity findSessionBySessionIdAndUserIdForUpdate(
            @Param("sessionId") String sessionId, @Param("userId") long userId);

    @Select("SELECT " + SESSION_COLUMNS + " FROM agent_session WHERE id = #{id} AND user_id = #{userId} LIMIT 1")
    AgentSessionEntity findSessionByIdAndUserId(@Param("id") long id, @Param("userId") long userId);

    @Insert("""
            INSERT INTO agent_session (
                id, session_id, user_id, summary, status, active_run_id, version, create_time, update_time, expire_at
            ) VALUES (
                #{session.id}, #{session.sessionId}, #{session.userId}, #{session.summary}, #{session.status},
                #{session.activeRunId}, #{session.version}, #{session.createTime}, #{session.updateTime},
                #{session.expireAt}
            )
            """)
    void insertSession(@Param("session") AgentSessionEntity session);

    @Update("""
            UPDATE agent_session
               SET active_run_id = #{runId}, expire_at = GREATEST(expire_at, #{runExpireAt}), version = version + 1
             WHERE id = #{sessionId} AND user_id = #{userId} AND active_run_id IS NULL
            """)
    int claimActiveRun(
            @Param("sessionId") long sessionId,
            @Param("userId") long userId,
            @Param("runId") long runId,
            @Param("runExpireAt") LocalDateTime runExpireAt);

    @Update("""
            UPDATE agent_session
               SET active_run_id = NULL, version = version + 1
             WHERE id = #{sessionId} AND active_run_id = #{runId}
            """)
    int releaseActiveRun(@Param("sessionId") long sessionId, @Param("runId") long runId);

    @Delete("""
            DELETE FROM agent_session
             WHERE id = #{sessionId} AND active_run_id IS NULL
               AND NOT EXISTS (SELECT 1 FROM agent_run WHERE session_id = #{sessionId})
            """)
    int deleteSessionIfEmptyAndInactive(@Param("sessionId") long sessionId);

    @Select("SELECT " + RUN_COLUMNS + " FROM agent_run WHERE run_id = #{runId} AND user_id = #{userId} LIMIT 1")
    AgentRunEntity findRunByRunIdAndUserId(@Param("runId") String runId, @Param("userId") long userId);

    @Select("SELECT " + RUN_COLUMNS + " FROM agent_run WHERE user_id = #{userId} AND session_id = #{sessionId}"
            + " AND client_request_id = #{clientRequestId} LIMIT 1")
    AgentRunEntity findRunByClientRequestId(
            @Param("userId") long userId,
            @Param("sessionId") long sessionId,
            @Param("clientRequestId") String clientRequestId);

    @Select("SELECT " + RUN_COLUMNS + " FROM agent_run WHERE status = 'RUNNING'"
            + " AND update_time <= #{cutoff} ORDER BY update_time ASC LIMIT #{limit}")
    List<AgentRunEntity> findStaleRunningBefore(@Param("cutoff") LocalDateTime cutoff, @Param("limit") int limit);

    @Insert("""
            INSERT INTO agent_run (
                id, run_id, session_id, user_id, client_request_id, request_hash_version, request_hash,
                plan_id, plan_version, status, trace_id, started_at, finished_at, version,
                create_time, update_time, expire_at
            ) VALUES (
                #{run.id}, #{run.runId}, #{run.sessionId}, #{run.userId}, #{run.clientRequestId},
                #{run.requestHashVersion}, #{run.requestHash}, #{run.planId}, #{run.planVersion}, #{run.status},
                #{run.traceId}, #{run.startedAt}, #{run.finishedAt}, #{run.version},
                #{run.createTime}, #{run.updateTime}, #{run.expireAt}
            )
            """)
    void insertRun(@Param("run") AgentRunEntity run);

    @Update("""
            UPDATE agent_run
               SET plan_id = #{run.planId}, plan_version = #{run.planVersion}, version = version + 1,
                   update_time = #{run.updateTime}
             WHERE id = #{run.id} AND version = #{expectedVersion} AND status = 'RUNNING'
            """)
    int updateRunRunningPlanWithCas(@Param("run") AgentRunEntity run, @Param("expectedVersion") long expectedVersion);

    @Update("""
            UPDATE agent_run
               SET plan_id = #{run.planId}, plan_version = #{run.planVersion}, status = #{run.status},
                   finished_at = #{run.finishedAt}, version = version + 1, update_time = #{run.updateTime}
             WHERE id = #{run.id} AND version = #{expectedVersion} AND status = 'RUNNING'
            """)
    int updateRunTerminalWithCas(@Param("run") AgentRunEntity run, @Param("expectedVersion") long expectedVersion);

    @Delete("""
            DELETE FROM agent_run
             WHERE id = #{runId} AND status IN ('COMPLETED', 'FAILED', 'CANCELLED')
               AND expire_at < #{now}
            """)
    int deleteTerminalExpiredRunById(@Param("runId") long runId, @Param("now") LocalDateTime now);

    @Select("SELECT COUNT(*) FROM agent_run WHERE session_id = #{sessionId}")
    int countRunsBySessionId(@Param("sessionId") long sessionId);

    @Insert("""
            INSERT INTO agent_message (
                id, message_id, session_id, run_id, user_id, role, message_type, text, payload_json,
                status, completed_at, create_time, expire_at
            ) VALUES (
                #{message.id}, #{message.messageId}, #{message.sessionId}, #{message.runId}, #{message.userId},
                #{message.role}, #{message.messageType}, #{message.text}, #{message.payloadJson},
                #{message.status}, #{message.completedAt}, #{message.createTime}, #{message.expireAt}
            )
            """)
    void insertMessage(@Param("message") AgentMessageEntity message);

    @Select("SELECT " + MESSAGE_COLUMNS + " FROM agent_message WHERE session_id = #{sessionId}"
            + " AND user_id = #{userId} ORDER BY id DESC LIMIT #{limit}")
    List<AgentMessageEntity> findMessagesBySessionIdAndUserId(
            @Param("sessionId") long sessionId, @Param("userId") long userId, @Param("limit") int limit);

    @Select("SELECT " + MESSAGE_COLUMNS + " FROM agent_message WHERE run_id = #{runId}"
            + " AND user_id = #{userId} ORDER BY id ASC")
    List<AgentMessageEntity> findMessagesByRunIdAndUserId(@Param("runId") long runId, @Param("userId") long userId);

    @Delete("DELETE FROM agent_message WHERE run_id = #{runId}")
    int deleteMessagesByRunId(@Param("runId") long runId);

    @Insert("""
            INSERT INTO agent_run_step (
                id, run_id, plan_version, node_id, node_type, depends_on_json, input_refs_json, status,
                failure_policy, attempt_count, retry_count, recovery_pending, auto_skipped, skip_reason,
                skip_source_node_id, slot_snapshot_json, started_at, finished_at, version,
                create_time, update_time, expire_at
            ) VALUES (
                #{step.id}, #{step.runId}, #{step.planVersion}, #{step.nodeId}, #{step.nodeType},
                #{step.dependsOnJson}, #{step.inputRefsJson}, #{step.status}, #{step.failurePolicy},
                #{step.attemptCount}, #{step.retryCount}, #{step.recoveryPending}, #{step.autoSkipped},
                #{step.skipReason}, #{step.skipSourceNodeId}, #{step.slotSnapshotJson}, #{step.startedAt},
                #{step.finishedAt}, #{step.version}, #{step.createTime}, #{step.updateTime}, #{step.expireAt}
            )
            """)
    void insertRunStep(@Param("step") AgentRunStepEntity step);

    @Select("SELECT " + STEP_COLUMNS + " FROM agent_run_step WHERE run_id = #{runId} ORDER BY id ASC")
    List<AgentRunStepEntity> findStepsByRunId(@Param("runId") long runId);

    @Update("""
            UPDATE agent_run_step
               SET status = #{step.status}, failure_policy = #{step.failurePolicy},
                   attempt_count = #{step.attemptCount}, retry_count = #{step.retryCount},
                   recovery_pending = #{step.recoveryPending}, auto_skipped = #{step.autoSkipped},
                   skip_reason = #{step.skipReason}, skip_source_node_id = #{step.skipSourceNodeId},
                   slot_snapshot_json = #{step.slotSnapshotJson}, started_at = #{step.startedAt},
                   finished_at = #{step.finishedAt}, version = version + 1, update_time = #{step.updateTime}
             WHERE id = #{step.id} AND version = #{expectedVersion} AND status = #{expectedStatus}
            """)
    int updateRunStepWithCas(
            @Param("step") AgentRunStepEntity step,
            @Param("expectedVersion") long expectedVersion,
            @Param("expectedStatus") String expectedStatus);

    @Delete("DELETE FROM agent_run_step WHERE run_id = #{runId}")
    int deleteRunStepsByRunId(@Param("runId") long runId);

    @Select("SELECT " + EVENT_CURSOR_COLUMNS
            + " FROM agent_event_stream_cursor WHERE session_id = #{sessionId} FOR UPDATE")
    AgentEventStreamCursorEntity findEventCursorForUpdate(@Param("sessionId") String sessionId);

    @Select("SELECT " + EVENT_CURSOR_COLUMNS + " FROM agent_event_stream_cursor WHERE session_id = #{sessionId}")
    AgentEventStreamCursorEntity findEventCursor(@Param("sessionId") String sessionId);

    @Insert("""
            INSERT INTO agent_event_stream_cursor (
                session_id, last_committed_event_id, first_retained_event_id, version, expire_at, create_time,
                update_time
            ) VALUES (
                #{cursor.sessionId}, #{cursor.lastCommittedEventId}, #{cursor.firstRetainedEventId},
                #{cursor.version}, #{cursor.expireAt}, #{cursor.createTime}, #{cursor.updateTime}
            )
            """)
    void insertEventCursor(@Param("cursor") AgentEventStreamCursorEntity cursor);

    @Update("""
            UPDATE agent_event_stream_cursor
               SET last_committed_event_id = #{cursor.lastCommittedEventId},
                   first_retained_event_id = #{cursor.firstRetainedEventId}, version = version + 1,
                   expire_at = #{cursor.expireAt}, update_time = #{cursor.updateTime}
             WHERE session_id = #{cursor.sessionId} AND version = #{expectedVersion}
            """)
    int updateEventCursor(
            @Param("cursor") AgentEventStreamCursorEntity cursor, @Param("expectedVersion") long expectedVersion);

    @Insert("""
            INSERT INTO agent_event (session_id, run_id, event_type, payload_json, expire_at, create_time)
            VALUES (#{event.sessionId}, #{event.runId}, #{event.eventType}, #{event.payloadJson},
                    #{event.expireAt}, #{event.createTime})
            """)
    void insertRuntimeEvent(@Param("event") AgentRuntimeEventEntity event);

    @Select("SELECT LAST_INSERT_ID()")
    long lastInsertedEventId();

    @Select("SELECT " + EVENT_COLUMNS + " FROM agent_event WHERE session_id = #{sessionId}"
            + " AND event_id > #{eventId} ORDER BY event_id ASC LIMIT #{limit}")
    List<AgentRuntimeEventEntity> findRuntimeEventsBySessionAfter(
            @Param("sessionId") String sessionId, @Param("eventId") long eventId, @Param("limit") int limit);

    @Select("SELECT " + EVENT_COLUMNS + " FROM agent_event WHERE run_id = #{runId}"
            + " AND event_id > #{eventId} ORDER BY event_id ASC LIMIT #{limit}")
    List<AgentRuntimeEventEntity> findRuntimeEventsByRunId(
            @Param("runId") String runId, @Param("eventId") long eventId, @Param("limit") int limit);

    @Select("SELECT COALESCE(MAX(event_id), 0) FROM agent_event WHERE run_id = #{runId}")
    long findLastRuntimeEventIdByRunId(@Param("runId") String runId);

    @Select("SELECT COUNT(*) FROM agent_event WHERE session_id = #{sessionId} AND event_id = #{eventId}")
    int countRuntimeEventBySessionAndEventId(@Param("sessionId") String sessionId, @Param("eventId") long eventId);

    @Select("""
            SELECT r.id AS runId, r.run_id AS externalRunId, r.user_id AS userId, s.session_id AS sessionId
              FROM agent_run r
              JOIN agent_session s ON s.id = r.session_id
              JOIN agent_event e ON e.run_id = r.run_id
             WHERE r.status IN ('COMPLETED', 'FAILED', 'CANCELLED') AND r.expire_at < #{now}
             GROUP BY r.id, r.run_id, r.user_id, s.session_id
             ORDER BY MIN(e.expire_at) ASC, MIN(e.event_id) ASC
             LIMIT #{limit}
            """)
    List<AgentExpiredRunCandidateEntity> findExpiredTerminalRuns(
            @Param("now") LocalDateTime now, @Param("limit") int limit);

    @Delete("DELETE FROM agent_event WHERE run_id = #{runId}")
    int deleteRuntimeEventsByRunId(@Param("runId") String runId);

    @Select("SELECT MIN(event_id) FROM agent_event WHERE session_id = #{sessionId}")
    Long findFirstRuntimeEventIdBySession(@Param("sessionId") String sessionId);

    @Delete("DELETE FROM agent_event_stream_cursor WHERE session_id = #{sessionId}")
    int deleteEventCursor(@Param("sessionId") String sessionId);
}
