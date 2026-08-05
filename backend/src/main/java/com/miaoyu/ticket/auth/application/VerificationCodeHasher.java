package com.miaoyu.ticket.auth.application;

import com.miaoyu.ticket.auth.domain.VerificationPurpose;

/** 使用独立密钥按邮箱和用途隔离验证码摘要。 */
public interface VerificationCodeHasher {

    String hash(String normalizedEmail, VerificationPurpose purpose, String code);

    boolean matches(String normalizedEmail, VerificationPurpose purpose, String code, String expectedHash);
}
