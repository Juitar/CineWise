package com.miaoyu.ticket.profile.domain;

/**
 * 用户可以明确管理的长期偏好维度。
 *
 * <p>枚举值直接对应 V010 的 CHECK 约束。这里不把影片、影院等业务事实存入画像，只表达用户对这类 条件的喜好或排斥，具体内容仍由内容和票务模块负责校验。
 */
public enum ProfileTagType {
  /** 影片类型，例如科幻或恐怖。 */
  MOVIE_GENRE,
  /** 观影时段偏好。 */
  TIME,
  /** 影院偏好。 */
  CINEMA,
  /** 影厅偏好。 */
  HALL,
  /** 票价区间偏好。 */
  PRICE,
  /** 座位条件偏好。 */
  SEAT
}
