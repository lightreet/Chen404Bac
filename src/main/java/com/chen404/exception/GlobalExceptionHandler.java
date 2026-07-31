package com.chen404.exception;

import com.chen404.domain.ApiErrorCode;
import com.chen404.domain.Result;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.validation.BindException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.stream.Collectors;

/**
 * 全局异常处理器。
 */
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    /**
     * 处理业务 API 异常。
     */
    @ExceptionHandler(ApiException.class)
    public ResponseEntity<Result<String>> handleApiException(ApiException e) {
        log.warn("[API_ERROR] status={} code={} exception={}",
                e.getHttpStatus().value(), e.getCode(), e.getClass().getSimpleName());
        return ResponseEntity.status(e.getHttpStatus())
                .body(Result.error(e.getCode(), e.getMessage()));
    }

    /**
     * 处理参数校验异常。
     */
    @ExceptionHandler(BindException.class)
    public ResponseEntity<Result<String>> handleBindException(BindException e) {
        String message = e.getFieldErrors().stream()
                .map(FieldError::getDefaultMessage)
                .collect(Collectors.joining(", "));
        return validationError(message);
    }

    /**
     * 处理 JSON 请求体的字段校验异常。
     */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<Result<String>> handleMethodArgumentNotValidException(MethodArgumentNotValidException e) {
        String message = e.getFieldErrors().stream()
                .map(FieldError::getDefaultMessage)
                .collect(Collectors.joining(", "));
        return validationError(message);
    }

    /**
     * 处理 JSON 请求体格式错误。
     */
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<Result<String>> handleHttpMessageNotReadableException(HttpMessageNotReadableException e) {
        log.warn("[REQUEST_BODY_PARSE_FAIL] exception={}", e.getClass().getSimpleName());
        return ResponseEntity.badRequest()
                .body(Result.error(ApiErrorCode.BAD_REQUEST, "请求参数格式错误"));
    }

    /**
     * 处理服务边界仍使用的参数异常，避免把明确的客户端输入错误升级成 500。
     */
    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<Result<String>> handleIllegalArgumentException(IllegalArgumentException e) {
        log.warn("[API_BAD_REQUEST] exception={}", e.getClass().getSimpleName());
        return ResponseEntity.badRequest()
                .body(Result.error(ApiErrorCode.BAD_REQUEST, e.getMessage()));
    }

    /**
     * 处理运行时异常。
     */
    @ExceptionHandler(RuntimeException.class)
    public ResponseEntity<Result<String>> handleRuntimeException(RuntimeException e) {
        log.error("运行时异常", e);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(Result.error(ApiErrorCode.INTERNAL_SERVER_ERROR, "系统繁忙，请稍后重试"));
    }

    /**
     * 处理其他异常。
     */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<Result<String>> handleException(Exception e) {
        log.error("系统异常", e);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(Result.error(ApiErrorCode.INTERNAL_SERVER_ERROR, "系统繁忙，请稍后重试"));
    }

    private ResponseEntity<Result<String>> validationError(String message) {
        return ResponseEntity.status(HttpStatus.UNPROCESSABLE_ENTITY)
                .body(Result.error(ApiErrorCode.VALIDATION_FAILED, message));
    }
}
