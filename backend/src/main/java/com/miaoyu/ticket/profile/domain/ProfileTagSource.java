package com.miaoyu.ticket.profile.domain;

/**
 * 标签产生来源，决定其写入入口和更新权限。
 *
 * <p>用户手工设置的标签不会被行为统计覆盖；对话标签必须来自 B 已确认的长期保存动作，行为标签只能由 受控事件聚合产生。
 */
public enum ProfileTagSource {
  /** 用户在个人中心主动设置。 */
  MANUAL,
  /** 用户确认后保存的长期对话偏好。 */
  CONVERSATION,
  /** 由受信任事件按固定规则聚合出的弱偏好。 */
  BEHAVIOR
}
