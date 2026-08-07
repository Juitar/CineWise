package com.miaoyu.ticket.auth.application;

import com.miaoyu.ticket.common.error.ErrorCode;
import org.springframework.http.HttpStatus;

/** 认证模块公开错误码，避免修改其他模块已经使用的公共错误码。 */
public enum AuthErrorCode implements ErrorCode {
    INVALID_PARAMETER(101001, "请求参数不合法", HttpStatus.BAD_REQUEST),
    RATE_LIMITED(101002, "操作过于频繁，请稍后重试", HttpStatus.TOO_MANY_REQUESTS),
    INVALID_CREDENTIALS(201001, "邮箱或密码错误", HttpStatus.UNAUTHORIZED),
    VERIFICATION_CODE_INVALID(201002, "验证码无效或已过期", HttpStatus.UNPROCESSABLE_ENTITY),
    EMAIL_ALREADY_REGISTERED(201003, "邮箱已注册", HttpStatus.CONFLICT),
    INVITE_CODE_UNAVAILABLE(201004, "邀请码不可用", HttpStatus.UNPROCESSABLE_ENTITY),
    ACCOUNT_UNAVAILABLE(201005, "账号已禁用或锁定", HttpStatus.FORBIDDEN),
    SESSION_INVALID(201006, "登录状态已失效，请重新登录", HttpStatus.UNAUTHORIZED),
    FORBIDDEN(201007, "当前账号无权执行该操作", HttpStatus.FORBIDDEN),
    PRIVACY_POLICY_INVALID(201008, "请同意当前版本隐私政策", HttpStatus.UNPROCESSABLE_ENTITY),
    CSRF_INVALID(201009, "安全校验已失效，请重新操作", HttpStatus.FORBIDDEN),
    /** 有限用户ID集合无法安全表达过宽的邮箱包含匹配。 */
    USER_QUERY_TOO_BROAD(201010, "用户查询条件过宽，请补充更多关键字", HttpStatus.BAD_REQUEST),
    PROFILE_DATA_CONSENT_CONFLICT(201011, "画像数据使用状态已更新，请刷新后重试", HttpStatus.CONFLICT),
    PROFILE_DATA_CONSENT_RECOVERY_UNAVAILABLE(
            201012, "撤回事件当前不可恢复或投递仍失败", HttpStatus.CONFLICT),
    MAIL_SERVICE_UNAVAILABLE(301001, "邮件服务暂不可用，请稍后重试", HttpStatus.SERVICE_UNAVAILABLE),
    /** 用户目录基础设施故障不能伪装成没有匹配用户。 */
    USER_DIRECTORY_UNAVAILABLE(301002, "用户信息查询暂不可用，请稍后重试", HttpStatus.SERVICE_UNAVAILABLE);

    private final int code;
    private final String message;
    private final HttpStatus httpStatus;

    AuthErrorCode(int code, String message, HttpStatus httpStatus) {
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
