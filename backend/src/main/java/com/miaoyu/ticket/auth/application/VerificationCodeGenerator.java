package com.miaoyu.ticket.auth.application;

/** 生成只存在于当前发送调用内存中的邮箱验证码明文。 */
@FunctionalInterface
public interface VerificationCodeGenerator {

    String generate();
}
