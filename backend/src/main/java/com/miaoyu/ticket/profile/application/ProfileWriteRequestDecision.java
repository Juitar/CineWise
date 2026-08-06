package com.miaoyu.ticket.profile.application;

/** 相同幂等键命中已有记录后的固定处理结果。 */
public enum ProfileWriteRequestDecision {
  /** 参数摘要一致，直接返回首次公开响应。 */
  REPLAY_FIRST_RESPONSE,
  /** 参数摘要不同，返回 202005，绝不能执行第二次写入。 */
  REJECT_PARAMETER_MISMATCH,
  /** 没有已有记录，才允许继续版本校验和业务写入。 */
  EXECUTE_NEW_REQUEST;

  /** 先比较持久化摘要，避免用当前版本错误拒绝原请求的安全重放。 */
  public static ProfileWriteRequestDecision decide(String storedHash, String incomingHash) {
    if (storedHash == null) {
      return EXECUTE_NEW_REQUEST;
    }
    return storedHash.equals(incomingHash) ? REPLAY_FIRST_RESPONSE : REJECT_PARAMETER_MISMATCH;
  }
}
