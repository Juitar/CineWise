package com.miaoyu.ticket.auth.application;

import com.miaoyu.ticket.profile.application.ProfileDataConsentWithdrawnEvent;

/** 向 D 投递类型化撤回事件；投递异常必须返回给 outbox 调度器处理。 */
@FunctionalInterface
public interface ProfileDataConsentWithdrawalPublisher {
  void publish(ProfileDataConsentWithdrawnEvent event);
}
