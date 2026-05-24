package com.chen404.exception;

import com.chen404.domain.ApiErrorCode;
import org.springframework.http.HttpStatus;

/**
 * 无权限（如非管理员访问管理接口），由全局异常处理器返回 403
 */
public class ForbiddenException extends ApiException {

    public ForbiddenException(String message) {
        super(HttpStatus.FORBIDDEN, ApiErrorCode.FORBIDDEN, message);
    }
}
