package com.miaoyu.ticket.auth.api;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** 密码登录只接收已确认的三个字段，不接受角色或隐私同意等客户端身份信息。 */
public record PasswordLoginRequest(
        @NotBlank(message = "clientRequestId 不能为空")
        @Size(max = 64, message = "clientRequestId 长度不能超过 64")
        String clientRequestId,

        @NotBlank(message = "邮箱不能为空")
        @Email(message = "邮箱格式不正确")
        @Size(max = 255, message = "邮箱长度不能超过 255")
        String email,

        @NotBlank(message = "密码不能为空")
        @Size(min = 8, max = 128, message = "密码长度必须为 8 到 128 位")
        String password) {

    /** 只清理标识字段首尾空白，密码保持用户原始输入，避免改变凭据含义。 */
    public PasswordLoginRequest {
        clientRequestId = clientRequestId == null ? null : clientRequestId.strip();
        email = email == null ? null : email.strip();
    }

    /** 邮箱和密码不能因 MVC 调试日志调用默认 toString 而泄露。 */
    @Override
    public String toString() {
        return "PasswordLoginRequest[clientRequestId=" + clientRequestId + ", credentials=[REDACTED]]";
    }
}
