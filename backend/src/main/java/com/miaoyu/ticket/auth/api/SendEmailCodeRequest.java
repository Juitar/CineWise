package com.miaoyu.ticket.auth.api;

import com.miaoyu.ticket.auth.domain.VerificationPurpose;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/** 验证码发送请求只接收邮箱和本期支持的用途。 */
public record SendEmailCodeRequest(
        @NotBlank(message = "邮箱不能为空")
        @Email(message = "邮箱格式不正确")
        @Size(max = 255, message = "邮箱长度不能超过 255")
        String email,

        @NotNull(message = "验证码用途不能为空") VerificationPurpose purpose) {

    public SendEmailCodeRequest {
        email = email == null ? null : email.strip();
    }

    /** 发送接口采用统一成功语义，日志也不能反向暴露请求邮箱。 */
    @Override
    public String toString() {
        return "SendEmailCodeRequest[email=[REDACTED], purpose=" + purpose + "]";
    }
}
