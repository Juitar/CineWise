package com.miaoyu.ticket.profile.domain;

import java.util.Objects;

/**
 * 经规范化后的标签值。
 *
 * <p>值对象在进入持久化前统一去除首尾空白并限制长度，避免同一偏好因不可见空格绕过唯一键。它不负责解释 影片、影院或场次事实，因而不依赖其他业务模块。
 */
public record ProfileTagValue(String value) {

  /** V010 的 tag_value 长度上限，保持领域校验和数据库约束一致。 */
  public static final int MAX_LENGTH = 128;

  public ProfileTagValue {
    Objects.requireNonNull(value, "标签值不能为空");
    value = value.trim();
    if (value.isEmpty() || value.length() > MAX_LENGTH) {
      // 空值或超长值不能进入唯一键和摘要，具体 REST 错误由应用层统一映射为 102001。
      throw new IllegalArgumentException("标签值不能为空且长度不能超过 128");
    }
  }
}
