package com.miaoyu.ticket.auth.infrastructure.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.miaoyu.ticket.auth.application.AuthErrorCode;
import com.miaoyu.ticket.common.api.Result;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import org.springframework.http.MediaType;

/** Security 过滤器在 Controller 前失败时也返回统一 Result 结构。 */
final class AuthSecurityResponseWriter {

    private final ObjectMapper objectMapper;

    AuthSecurityResponseWriter(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    void write(HttpServletResponse response, AuthErrorCode errorCode) throws IOException {
        response.setStatus(errorCode.httpStatus().value());
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        objectMapper.writeValue(response.getOutputStream(), Result.failure(errorCode));
    }
}
