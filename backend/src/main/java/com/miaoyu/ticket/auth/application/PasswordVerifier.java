package com.miaoyu.ticket.auth.application;

/** 密码散列算法留在安全基础设施，应用层只表达校验动作。 */
public interface PasswordVerifier {

    boolean matches(String rawPassword, String passwordHash);

    String encode(String rawPassword);
}
