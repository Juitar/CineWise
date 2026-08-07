package com.miaoyu.ticket.profile.application;

/**
 * C 提供的个人数据保存同意查询边界。
 * 画像不能通过读取认证表或登录状态推断同意；C 尚未接入时由默认实现返回未同意。
 */
public interface ProfileDataConsentQuery {
  ProfileDataConsentSnapshot findByUserId(long userId);
}
