package com.miaoyu.ticket.travel.infrastructure.persistence;

import com.miaoyu.ticket.travel.application.TravelNotificationRepository;
import java.time.LocalDateTime;
import java.util.Optional;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;
import org.springframework.stereotype.Repository;

/** 仅维护出行投递记录；邮件地址和正文始终留在 C 的公共邮件服务内。 */
@Repository
public class MybatisTravelNotificationRepository implements TravelNotificationRepository {
    private final MapperPort mapper;

    public MybatisTravelNotificationRepository(MapperPort mapper) {
        this.mapper = mapper;
    }

    @Override
    public Optional<Notification> findByDeliveryKey(String key) {
        return Optional.ofNullable(mapper.findByDeliveryKey(key));
    }
    @Override
    public void insert(Notification value) {
        if (mapper.insert(value) != 1) {
            throw new IllegalStateException("出行投递记录写入异常");
        }
    }

    @Override
    public boolean markSending(String key, LocalDateTime now) {
        return mapper.markSending(key, now) == 1;
    }

    @Override
    public void resolve(String key, String status, String messageId, Integer errorCode, LocalDateTime now) {
        if (mapper.resolve(key, status, messageId, errorCode, now) != 1) {
            throw new IllegalStateException("出行投递结果更新异常");
        }
    }

    @Override
    public boolean markNotified(long taskId, LocalDateTime now) {
        return mapper.markNotified(taskId, now) == 1;
    }

    @Mapper
    public interface MapperPort {
        @Select("SELECT id, travel_task_id AS taskId, task_version AS taskVersion, delivery_key AS deliveryKey, "
                + "status FROM travel_notification_log WHERE delivery_key=#{key}")
        Notification findByDeliveryKey(@Param("key") String key);
        @Insert("INSERT INTO travel_notification_log (id,travel_task_id,trigger_type,task_version,channel,"
                + "template_code,delivery_key,status,attempt_count,scheduled_at,create_time,update_time) VALUES "
                + "(#{id},#{taskId},'VIEWING_REMINDER',#{taskVersion},'EMAIL','VIEWING_REMINDER',#{deliveryKey},"
                + "'PENDING',0,CURRENT_TIMESTAMP(3),CURRENT_TIMESTAMP(3),CURRENT_TIMESTAMP(3))")
        int insert(Notification value);
        @Update("UPDATE travel_notification_log SET status='SENDING',attempt_count=attempt_count+1,"
                + "lease_until=#{now},update_time=#{now} WHERE delivery_key=#{key} AND status='PENDING'")
        int markSending(@Param("key") String key, @Param("now") LocalDateTime now);
        @Update("UPDATE travel_notification_log SET status=#{status},provider_message_id=#{messageId},"
                + "error_code=#{errorCode},sent_at=CASE WHEN #{status}='SENT' THEN #{now} ELSE sent_at END,"
                + "resolved_at=CASE WHEN #{status} IN ('SENT','FAILED') THEN #{now} ELSE NULL END,lease_until=NULL,"
                + "update_time=#{now} WHERE delivery_key=#{key}")
        int resolve(@Param("key") String key, @Param("status") String status,
                    @Param("messageId") String messageId, @Param("errorCode") Integer errorCode,
                    @Param("now") LocalDateTime now);
        @Update("UPDATE travel_task SET status='NOTIFIED',update_time=#{now} WHERE id=#{taskId} AND status='READY'")
        int markNotified(@Param("taskId") long taskId, @Param("now") LocalDateTime now);
    }
}
