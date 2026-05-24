package com.chen404.exception;

import org.springframework.http.HttpStatus;

/**
 * 业务 API 异常基类，统一承载 HTTP 状态与响应 code。
 */
public class ApiException extends RuntimeException {

    private final HttpStatus httpStatus;
    private final int code;

    public ApiException(HttpStatus httpStatus, int code, String message) {
        super(message);
        this.httpStatus = httpStatus;
        this.code = code;
    }

    public HttpStatus getHttpStatus() {
        return httpStatus;
    }

    public int getCode() {
        return code;
    }
}
