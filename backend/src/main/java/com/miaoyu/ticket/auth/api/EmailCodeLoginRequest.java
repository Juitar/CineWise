package com.miaoyu.ticket.auth.api;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/** 邮箱验证码登录不接收密码、角色或隐私同意字段。 */
public record EmailCodeLoginRequest(
        @NotBlank(message = "clientRequestId 不能为空")
        @Size(max = 64, message = "clientRequestId 长度不能超过 64")
        String clientRequestId,

        @NotBlank(message = "邮箱不能为空")
        @Email(message = "邮箱格式不正确")
        @Size(max = 255, message = "邮箱长度不能超过 255")
        String email,

        @NotBlank(message = "验证码不能为空")
        @Pattern(regexp = "\\d{6}", message = "验证码必须为 6 位数字")
        String code) {

    public EmailCodeLoginRequest {
        clientRequestId = clientRequestId == null ? null : clientRequestId.strip();
        email = email == null ? null : email.strip();
        code = code == null ? null : code.strip();
    }

    /** 邮箱和验证码不能因 MVC 调试日志调用默认 toString 而泄露。 */
    @Override
    public String toString() {
        return "EmailCodeLoginRequest[clientRequestId=" + clientRequestId + ", credentials=[REDACTED]]";
    }
}
