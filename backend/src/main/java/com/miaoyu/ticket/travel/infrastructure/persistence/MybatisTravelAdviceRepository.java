package com.miaoyu.ticket.travel.infrastructure.persistence;

import com.miaoyu.ticket.travel.application.TravelAdviceRepository;
import com.miaoyu.ticket.travel.application.TravelAdviceSnapshot;
import java.time.LocalDateTime;
import java.util.Optional;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;
import org.springframework.stereotype.Repository;

/**
 * 只负责建议快照 SQL 的 MyBatis 适配器。
 *
 * <p>条件更新把同一任务版本的竞争交给数据库裁决；这里不读取订单或用户表，保持 D 对任务数据的独占。</p>
 */
@Repository
public class MybatisTravelAdviceRepository implements TravelAdviceRepository {

    private final TravelAdvicePersistenceMapper mapper;

    public MybatisTravelAdviceRepository(TravelAdvicePersistenceMapper mapper) {
        this.mapper = mapper;
    }

    @Override
    public boolean claimVersionForAdvice(long taskId, long expectedVersion, LocalDateTime updatedAt) {
        return mapper.claimVersionForAdvice(taskId, expectedVersion, updatedAt) == 1;
    }

    @Override
    public void insert(TravelAdviceSnapshot snapshot) {
        if (mapper.insert(snapshot) != 1) {
            throw new IllegalStateException("出行建议快照写入行数异常");
        }
    }

    @Override
    public Optional<TravelAdviceSnapshot> findByTaskIdAndVersion(long taskId, long taskVersion) {
        return Optional.ofNullable(mapper.findByTaskIdAndVersion(taskId, taskVersion));
    }

    /** MyBatis 注解 SQL 放在适配器内部，应用层不会接触表名或 JSON 字段。 */
    @Mapper
    public interface TravelAdvicePersistenceMapper {

        @Update("""
                UPDATE travel_task SET version = version + 1, status = 'READY', update_time = #{updatedAt}
                 WHERE id = #{taskId} AND version = #{expectedVersion}
                   AND status IN ('PENDING', 'READY', 'NOTIFIED')
                """)
        int claimVersionForAdvice(@Param("taskId") long taskId, @Param("expectedVersion") long expectedVersion,
                                  @Param("updatedAt") LocalDateTime updatedAt);

        @Insert("""
                INSERT INTO travel_advice_snapshot (
                    id, travel_task_id, task_version, weather_json, route_json, food_json, advice_json,
                    source, data_time, expires_at, is_expired, degraded, fallback_type, create_time
                ) VALUES (
                    #{id}, #{taskId}, #{taskVersion}, #{weatherJson}, NULL, NULL, #{adviceJson},
                    #{source}, #{dataTime}, #{expiresAt}, #{isExpired}, #{degraded}, #{fallbackType}, #{createdAt}
                )
                """)
        int insert(TravelAdviceSnapshot snapshot);

        @Select("""
                SELECT id, travel_task_id AS task_id, task_version, weather_json AS weather_json,
                       advice_json AS advice_json, source, data_time AS data_time, expires_at AS expires_at,
                       is_expired AS is_expired, degraded, fallback_type AS fallback_type,
                       create_time AS created_at
                  FROM travel_advice_snapshot
                 WHERE travel_task_id = #{taskId} AND task_version = #{taskVersion}
                """)
        TravelAdviceSnapshot findByTaskIdAndVersion(
                @Param("taskId") long taskId, @Param("taskVersion") long taskVersion);
    }
}
