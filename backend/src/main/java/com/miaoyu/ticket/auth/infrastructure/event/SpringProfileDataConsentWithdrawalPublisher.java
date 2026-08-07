package com.miaoyu.ticket.auth.infrastructure.event;

import com.miaoyu.ticket.auth.application.ProfileDataConsentWithdrawalPublisher;
import com.miaoyu.ticket.profile.application.ProfileDataConsentWithdrawnEvent;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;

/** Spring 事件只承担当前投递尝试，可靠来源始终是 MySQL outbox。 */
@Component
public class SpringProfileDataConsentWithdrawalPublisher
    implements ProfileDataConsentWithdrawalPublisher {
  private final ApplicationEventPublisher eventPublisher;

  public SpringProfileDataConsentWithdrawalPublisher(ApplicationEventPublisher eventPublisher) {
    this.eventPublisher = eventPublisher;
  }

  @Override
  public void publish(ProfileDataConsentWithdrawnEvent event) {
    eventPublisher.publishEvent(event);
  }
}
