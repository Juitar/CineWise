package com.miaoyu.ticket.auth.api;

import com.miaoyu.ticket.auth.application.AuthErrorCode;
import com.miaoyu.ticket.common.api.Result;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/** 认证请求校验固定返回 101001，避免全局 100001 破坏已有模块契约。 */
@Order(Ordered.HIGHEST_PRECEDENCE)
@RestControllerAdvice(assignableTypes = AuthController.class)
public class AuthExceptionHandler {

    private static final Logger LOGGER = LoggerFactory.getLogger(AuthExceptionHandler.class);

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<Result<Void>> handleValidation(MethodArgumentNotValidException exception) {
        String message = exception.getBindingResult().getFieldErrors().isEmpty()
                ? AuthErrorCode.INVALID_PARAMETER.message()
                : exception.getBindingResult().getFieldErrors().getFirst().getDefaultMessage();
        return invalidParameter(message);
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<Result<Void>> handleUnreadable(HttpMessageNotReadableException exception) {
        LOGGER.debug("认证请求 JSON 解析失败", exception);
        return invalidParameter(AuthErrorCode.INVALID_PARAMETER.message());
    }

    private ResponseEntity<Result<Void>> invalidParameter(String message) {
        return ResponseEntity.status(AuthErrorCode.INVALID_PARAMETER.httpStatus())
                .body(Result.failure(AuthErrorCode.INVALID_PARAMETER, message));
    }
}
