package com.miaoyu.ticket.auth.infrastructure.persistence;

import com.miaoyu.ticket.auth.application.ProfileDataConsentRepository;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Optional;
import org.springframework.stereotype.Repository;

/** 将 C 的同意仓储端口映射到 V017 表，不向 D 暴露持久化对象。 */
@Repository
public class MybatisProfileDataConsentRepository implements ProfileDataConsentRepository {
  private final ProfileDataConsentPersistenceMapper mapper;

  public MybatisProfileDataConsentRepository(ProfileDataConsentPersistenceMapper mapper) {
    this.mapper = mapper;
  }

  @Override
  public Optional<ConsentRecord> findByUserId(long userId) {
    return Optional.ofNullable(mapper.findByUserId(userId)).map(this::toRecord);
  }

  @Override
  public void insertGranted(long id, long userId, String privacyPolicyVersion, Instant now) {
    if (mapper.insertGranted(id, userId, privacyPolicyVersion, toLocal(now)) != 1) {
      throw new IllegalStateException("画像数据保存同意写入行数异常");
    }
  }

  @Override
  public boolean regrant(long userId, long expectedRecordVersion, String privacyPolicyVersion, Instant now) {
    return mapper.regrant(userId, expectedRecordVersion, privacyPolicyVersion, toLocal(now)) == 1;
  }

  @Override
  public boolean withdraw(long userId, long expectedRecordVersion, Instant now) {
    return mapper.withdraw(userId, expectedRecordVersion, toLocal(now)) == 1;
  }

  private ConsentRecord toRecord(ProfileDataConsentPersistenceMapper.Row row) {
    return new ConsentRecord(
        row.userId(),
        Status.valueOf(row.status()),
        row.consentVersion(),
        row.version(),
        toInstant(row.grantedAt()),
        toInstant(row.withdrawnAt()));
  }

  private LocalDateTime toLocal(Instant instant) {
    return LocalDateTime.ofInstant(instant, ZoneOffset.UTC);
  }

  private Instant toInstant(LocalDateTime time) {
    return time == null ? null : time.toInstant(ZoneOffset.UTC);
  }
}
