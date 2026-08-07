package com.miaoyu.ticket.auth.application;

import java.time.Instant;
import java.util.Optional;

/** C 私有的画像数据保存同意仓储端口，D 只能使用公开查询快照。 */
public interface ProfileDataConsentRepository {

  Optional<ConsentRecord> findByUserId(long userId);

  void insertGranted(long id, long userId, String privacyPolicyVersion, Instant now);

  boolean regrant(long userId, long expectedRecordVersion, String privacyPolicyVersion, Instant now);

  boolean withdraw(long userId, long expectedRecordVersion, Instant now);

  record ConsentRecord(
      long userId,
      Status status,
      long consentVersion,
      long recordVersion,
      Instant grantedAt,
      Instant withdrawnAt) { }

  enum Status {
    GRANTED,
    WITHDRAWN
  }
}
