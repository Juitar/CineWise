package com.miaoyu.ticket.profile.application;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

/** 对已规范化的公开写入参数计算 SHA-256，原始请求内容不落库。 */
public final class ProfileRequestHasher {
  private ProfileRequestHasher() { }

  /** 生成小写 64 位摘要，供同一幂等键判断“同内容”或“不同内容”。 */
  public static String sha256(String normalizedPublicParameters) {
    try {
      byte[] bytes =
          MessageDigest.getInstance("SHA-256")
              .digest(normalizedPublicParameters.getBytes(StandardCharsets.UTF_8));
      StringBuilder result = new StringBuilder(64);
      for (byte value : bytes) {
        result.append(String.format("%02x", value));
      }
      return result.toString();
    } catch (NoSuchAlgorithmException exception) {
      // JRE 缺少 SHA-256 时不能退化为弱摘要，否则会错误复用画像写入结果。
      throw new IllegalStateException("JRE 缺少 SHA-256 算法", exception);
    }
  }
}
