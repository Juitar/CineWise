package com.miaoyu.ticket.profile.infrastructure.persistence;

import com.miaoyu.ticket.profile.application.ProfileWriteRequestRepository;
import java.util.Optional;
import org.springframework.stereotype.Repository;

/** 将幂等 SQL 行转换为应用层快照，避免业务层直接依赖 MyBatis 类型。 */
@Repository
public class MybatisProfileWriteRequestRepository implements ProfileWriteRequestRepository {
  private final ProfileWriteRequestPersistenceMapper mapper;

  public MybatisProfileWriteRequestRepository(ProfileWriteRequestPersistenceMapper mapper) {
    this.mapper = mapper;
  }

  @Override
  public Optional<Snapshot> findByUserIdAndOperationAndIdempotencyKey(
      long userId, String operation, String idempotencyKey) {
    return Optional.ofNullable(mapper.findByKey(userId, operation, idempotencyKey))
        .map(
            row ->
                new Snapshot(
                    row.userId(),
                    row.operation(),
                    row.idempotencyKey(),
                    row.requestHash(),
                    row.httpStatus(),
                    row.responseJson(),
                    row.expiresAt()));
  }

  @Override
  public void insert(NewRequest request) {
    if (mapper.insert(request) != 1) {
      throw new IllegalStateException("画像幂等记录写入行数异常");
    }
  }

  @Override
  public int cleanupExpired(java.time.LocalDateTime before, int limit) {
    return mapper.cleanupExpired(before, limit);
  }
}
