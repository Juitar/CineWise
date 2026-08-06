package com.miaoyu.ticket.auth.api;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/** 密码重置请求禁止通过默认 toString 泄露邮箱、验证码或新密码。 */
public record PasswordResetRequest(
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

        @NotBlank(message = "新密码不能为空")
        @Size(min = 8, max = 20, message = "密码长度必须为 8 到 20 位")
        @Pattern(regexp = "^(?=.*[A-Za-z])(?=.*\\d).+$", message = "密码必须同时包含字母和数字")
        String newPassword) {

    public PasswordResetRequest {
        clientRequestId = strip(clientRequestId);
        email = strip(email);
        code = strip(code);
    }

    private static String strip(String value) {
        return value == null ? null : value.strip();
    }

    @Override
    public String toString() {
        return "PasswordResetRequest[clientRequestId=" + clientRequestId
                + ", sensitiveFields=[REDACTED]]";
    }
}
