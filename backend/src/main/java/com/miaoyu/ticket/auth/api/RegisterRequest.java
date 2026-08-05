package com.miaoyu.ticket.auth.api;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/** 注册请求不接受角色、账号状态或用户 ID，身份字段全部由服务端生成。 */
public record RegisterRequest(
        @NotBlank(message = "clientRequestId 不能为空")
        @Size(max = 64, message = "clientRequestId 长度不能超过 64")
        String clientRequestId,

        @NotBlank(message = "邮箱不能为空")
        @Email(message = "邮箱格式不正确")
        @Size(max = 255, message = "邮箱长度不能超过 255")
        String email,

        @NotBlank(message = "验证码不能为空")
        @Pattern(regexp = "\\d{6}", message = "验证码必须为 6 位数字")
        String code,

        @NotBlank(message = "邀请码不能为空")
        @Size(max = 128, message = "邀请码长度不能超过 128")
        String inviteCode,

        @NotBlank(message = "密码不能为空")
        @Size(min = 8, max = 20, message = "密码长度必须为 8 到 20 位")
        @Pattern(regexp = "^(?=.*[A-Za-z])(?=.*\\d).+$", message = "密码必须同时包含字母和数字")
        String password,

        @Size(max = 64, message = "昵称长度不能超过 64")
        String nickname,

        @NotBlank(message = "隐私政策版本不能为空")
        @Size(max = 32, message = "隐私政策版本长度不能超过 32")
        String privacyPolicyVersion,

        @NotNull(message = "privacyAccepted 不能为空")
        Boolean privacyAccepted) {

    public RegisterRequest {
        clientRequestId = strip(clientRequestId);
        email = strip(email);
        code = strip(code);
        inviteCode = strip(inviteCode);
        nickname = strip(nickname);
        privacyPolicyVersion = strip(privacyPolicyVersion);
    }

    private static String strip(String value) {
        return value == null ? null : value.strip();
    }

    /** 禁止 MVC 调试日志通过 record 默认 toString 输出注册凭据和完整邮箱。 */
    @Override
    public String toString() {
        return "RegisterRequest[clientRequestId=" + clientRequestId + ", sensitiveFields=[REDACTED]]";
    }
}
