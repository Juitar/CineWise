package com.miaoyu.ticket.travel.application;

import java.time.LocalDateTime;
import java.util.Optional;

/** 出行邮件投递记录端口，唯一键保证重复调度不会重复发送。 */
public interface TravelNotificationRepository {
    Optional<Notification> findByDeliveryKey(String deliveryKey);
    void insert(Notification notification);
    boolean markSending(String deliveryKey, LocalDateTime now);
    void resolve(String deliveryKey, String status, String messageId, Integer errorCode, LocalDateTime now);
    boolean markNotified(long taskId, LocalDateTime now);

    record Notification(long id, long taskId, long taskVersion, String deliveryKey, String status) { }
}
