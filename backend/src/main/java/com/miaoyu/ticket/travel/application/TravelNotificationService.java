package com.miaoyu.ticket.travel.application;

import com.miaoyu.ticket.auth.application.mail.DeliveryResultStatus;
import com.miaoyu.ticket.auth.application.mail.EmailDeliveryCommand;
import com.miaoyu.ticket.auth.application.mail.EmailDeliveryPort;
import com.miaoyu.ticket.auth.application.mail.EmailDeliveryResult;
import com.miaoyu.ticket.common.id.BusinessIdGenerator;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.Map;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 将出行提醒投递交给 C 的公共端口；UNKNOWN 只查询恢复，绝不盲目重发。 */
@Service
public class TravelNotificationService {
    private final TravelNotificationRepository repository;
    private final EmailDeliveryPort emailDeliveryPort;
    private final BusinessIdGenerator idGenerator;
    private final Clock clock;

    public TravelNotificationService(TravelNotificationRepository repository, EmailDeliveryPort emailDeliveryPort,
                                     BusinessIdGenerator idGenerator, Clock clock) {
        this.repository = repository;
        this.emailDeliveryPort = emailDeliveryPort;
        this.idGenerator = idGenerator;
        this.clock = clock;
    }

    @Transactional
    public void deliver(TravelTaskRepository.TravelTaskSnapshot task, long taskVersion) {
        String key = "VIEWING_REMINDER:" + task.taskId() + ":" + taskVersion + ":VIEWING_REMINDER";
        TravelNotificationRepository.Notification notification = repository.findByDeliveryKey(key)
                .orElseGet(() -> create(task, taskVersion, key));
        if ("SENT".equals(notification.status()) || "FAILED".equals(notification.status())) {
            return;
        }
        if ("UNKNOWN".equals(notification.status())) {
            resolve(key, emailDeliveryPort.query(key), task.id());
            return;
        }
        if (!repository.markSending(key, now())) {
            return;
        }
        EmailDeliveryResult result;
        try {
            result = emailDeliveryPort.send(new EmailDeliveryCommand(key, "VIEWING_REMINDER",
                    Long.toString(task.userId()), Map.of("taskId", task.taskId()), null));
        } catch (RuntimeException exception) {
            result = EmailDeliveryResult.unknown();
        }
        if (result.status() == DeliveryResultStatus.UNKNOWN) {
            result = emailDeliveryPort.query(key);
        }
        resolve(key, result, task.id());
    }

    private TravelNotificationRepository.Notification create(TravelTaskRepository.TravelTaskSnapshot task,
                                                            long version, String key) {
        TravelNotificationRepository.Notification value = new TravelNotificationRepository.Notification(
                idGenerator.nextId(), task.id(), version, key, "PENDING");
        try {
            repository.insert(value);
            return value;
        } catch (DuplicateKeyException exception) {
            return repository.findByDeliveryKey(key).orElseThrow(() -> exception);
        }
    }

    private void resolve(String key, EmailDeliveryResult result, long taskId) {
        String status = result.status().name();
        repository.resolve(key, status, result.providerMessageId(), result.errorCode(), now());
        if (result.status() == DeliveryResultStatus.SENT) {
            repository.markNotified(taskId, now());
        }
    }

    private LocalDateTime now() {
        return LocalDateTime.now(clock);
    }
}
