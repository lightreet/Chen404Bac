package com.chen404.exception;

import com.chen404.domain.ApiErrorCode;
import org.springframework.http.HttpStatus;

/**
 * 未登录或 Token 无效时抛出，由全局异常处理器返回 401
 */
public class UnauthorizedException extends ApiException {

    public UnauthorizedException() {
        this("未登录");
    }

    public UnauthorizedException(String message) {
        super(HttpStatus.UNAUTHORIZED, ApiErrorCode.UNAUTHORIZED, message);
    }
}
