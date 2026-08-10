package com.miaoyu.ticket.profile.application;

/**
 * C 提供的撤回版本校验入口；校验和画像清理在同一事务中完成，避免旧事件覆盖新同意状态。
 */
public interface ProfileDataConsentWithdrawalGuard {
  boolean executeIfCurrent(ProfileDataConsentWithdrawnEvent event, Runnable cleanup);
}
