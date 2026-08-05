package com.miaoyu.ticket.auth.application;

/** 邀请码只在应用边界短暂出现，持久化查询始终使用不可逆摘要。 */
@FunctionalInterface
public interface RegistrationInviteHasher {

    String hash(String inviteCode);
}
