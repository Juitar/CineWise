package com.miaoyu.ticket.profile.application;

import java.time.Instant;

/** D 只使用该快照判断能否保存画像，两个版本不能互相替代。 */
public record ProfileDataConsentSnapshot(
    boolean granted, long consentVersion, long version, Instant grantedAt, Instant withdrawnAt) {

  /** 没有独立同意记录时的固定返回值，不能被登录或隐私政策同意替代。 */
  public static ProfileDataConsentSnapshot notGranted() {
    return new ProfileDataConsentSnapshot(false, 0, 0, null, null);
  }
}
