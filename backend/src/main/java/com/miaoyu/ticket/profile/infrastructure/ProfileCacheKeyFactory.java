package com.miaoyu.ticket.profile.infrastructure;

/** 画像摘要按用户和版本隔离，写入后删除该用户全部版本键。 */
public final class ProfileCacheKeyFactory {
  private ProfileCacheKeyFactory() { }

  public static String summaryKey(long userId, long version) {
    return "profile:" + userId + ":v:" + version;
  }

  public static String userPattern(long userId) {
    return "profile:" + userId + ":v:*";
  }
}
