package com.miaoyu.ticket.profile.application;

import com.miaoyu.ticket.common.error.ErrorCode;
import org.springframework.http.HttpStatus;

/**
 * 用户画像模块的公开错误码。
 *
 * <p>错误码区分参数、资源、并发、同意状态和幂等键复用，前端据此决定刷新、重新确认或停止操作；不得通过 模糊的通用异常让客户端猜测是否已经写入。
 */
public enum ProfileErrorCode implements ErrorCode {
  /** 用户提交的标签类型、值、权重或状态不满足画像规则。 */
  INVALID_TAG(102001, "标签参数不合法", HttpStatus.BAD_REQUEST),
  /** 受控调用方提交的行为字段或类型不满足最小化记录规则。 */
  INVALID_EVENT(102002, "行为事件参数不合法", HttpStatus.BAD_REQUEST),
  /** 同一用户仍有相同类型、值和来源的有效标签。 */
  DUPLICATE_TAG(202001, "当前用户已有相同有效标签", HttpStatus.CONFLICT),
  /** 客户端基于过期画像版本写入，必须先读取最新状态。 */
  PROFILE_VERSION_CONFLICT(202002, "画像版本已更新，请刷新后重试", HttpStatus.CONFLICT),
  /** 标签不存在或不属于当前用户时使用同一错误，避免泄漏归属。 */
  TAG_NOT_FOUND(202003, "标签不存在或无权访问", HttpStatus.NOT_FOUND),
  /** 未取得独立的画像保存同意，不能把登录或隐私政策同意当作替代条件。 */
  PROFILE_DATA_CONSENT_REQUIRED(202004, "请先同意保存个性化画像", HttpStatus.FORBIDDEN),
  /** 同一幂等键承载不同请求内容，客户端不得自动重试该写入。 */
  IDEMPOTENCY_PARAMETER_MISMATCH(202005, "幂等键已用于其他请求内容，请重新操作", HttpStatus.CONFLICT);

  /** 写入统一响应体的稳定六位数错误码，不能依赖枚举顺序推导。 */
  private final int code;

  private final String message;
  private final HttpStatus httpStatus;

  ProfileErrorCode(int code, String message, HttpStatus httpStatus) {
    this.code = code;
    this.message = message;
    this.httpStatus = httpStatus;
  }

  @Override
  public int code() {
    return code;
  }

  @Override
  public String message() {
    return message;
  }

  @Override
  public HttpStatus httpStatus() {
    return httpStatus;
  }
}
