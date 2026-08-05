package com.miaoyu.ticket.profile.domain;

/**
 * 用户对一个条件的正向或负向取向。
 *
 * <p>使用有限枚举而不是布尔值，避免调用方把“未设置”和“不喜欢”混为一谈；缺失偏好不应被当作负向偏好。
 */
public enum ProfileTagPolarity {
  /** 用户倾向选择该条件。 */
  LIKE,
  /** 用户明确排斥该条件。 */
  DISLIKE
}
