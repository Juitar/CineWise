package com.miaoyu.ticket.profile.domain;

/** 允许进入画像的最小行为类型，完整对话和支付明细不属于行为事件。 */
public enum ProfileBehaviorEventType {
  CLICK,
  FAVORITE,
  ACCEPT_PLAN,
  REJECT_PLAN,
  PAID_ORDER,
  NOT_INTERESTED
}
