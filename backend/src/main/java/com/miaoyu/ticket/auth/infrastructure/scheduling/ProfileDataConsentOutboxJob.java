package com.miaoyu.ticket.auth.infrastructure.scheduling;

import com.miaoyu.ticket.auth.application.ProfileDataConsentOutboxDeliveryService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** 定时扫描已到期的撤回事件，每条事件由独立事务投递和更新状态。 */
@Component
public class ProfileDataConsentOutboxJob {
  private static final Logger LOGGER = LoggerFactory.getLogger(ProfileDataConsentOutboxJob.class);
  private final ProfileDataConsentOutboxDeliveryService deliveryService;

  public ProfileDataConsentOutboxJob(ProfileDataConsentOutboxDeliveryService deliveryService) {
    this.deliveryService = deliveryService;
  }

  @Scheduled(fixedDelayString = "${cinewise.auth.profile-consent-outbox-delay-milliseconds:60000}")
  public void deliverReadyEvents() {
    for (String eventId : deliveryService.listReadyEventIds()) {
      try {
        deliveryService.deliverPending(eventId);
      } catch (RuntimeException exception) {
        LOGGER.warn("画像同意撤回 outbox 调度失败，保留原事件等待下次扫描，eventId={}", eventId, exception);
      }
    }
  }
}
