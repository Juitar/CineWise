package com.miaoyu.ticket.profile.domain;

/**
 * 标签当前是否可参与画像摘要和推荐。
 *
 * <p>删除保留最小审计痕迹但不能再进入摘要；过期与停用也必须在读取时排除，不能只依赖页面隐藏。
 */
public enum ProfileTagStatus {
  /** 可参与画像摘要。 */
  ACTIVE,
  /** 用户主动暂时停用。 */
  DISABLED,
  /** 行为标签因长期未更新而失效。 */
  EXPIRED,
  /** 用户删除后的软删除状态。 */
  DELETED
}
