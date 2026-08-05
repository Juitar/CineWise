package com.miaoyu.ticket.auth.application;

import java.time.LocalDateTime;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/** 错误验证码的失败次数独立提交，不能被登录或注册调用方随后抛出的异常回滚。 */
@Service
public class FailedVerificationAttemptRecorder {

    private final VerificationCodeRepository repository;

    public FailedVerificationAttemptRecorder(VerificationCodeRepository repository) {
        this.repository = repository;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void record(long id, int expectedAttemptCount, int maximumAttempts, LocalDateTime updateTime) {
        repository.recordFailedAttempt(id, expectedAttemptCount, maximumAttempts, updateTime);
    }
}
