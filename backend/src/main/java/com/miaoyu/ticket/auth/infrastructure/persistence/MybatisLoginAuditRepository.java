package com.miaoyu.ticket.auth.infrastructure.persistence;

import com.miaoyu.ticket.auth.application.LoginAuditRepository;
import java.time.LocalDateTime;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/** 审计新增使用独立事务，使日志失败不会把登录主流程标记为回滚。 */
@Repository
public class MybatisLoginAuditRepository implements LoginAuditRepository {

    private final LoginAuditMapper mapper;

    public MybatisLoginAuditRepository(LoginAuditMapper mapper) {
        this.mapper = mapper;
    }

    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void append(LoginAuditRecord record) {
        mapper.insert(record);
    }

    @Override
    @Transactional
    public int deleteCreatedBefore(LocalDateTime cutoff) {
        return mapper.deleteCreatedBefore(cutoff);
    }
}
