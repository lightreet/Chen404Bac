package com.chen404.exception;

import com.chen404.domain.ApiErrorCode;
import org.springframework.http.HttpStatus;

/**
 * 资源不存在时抛出，由全局异常处理器返回 404。
 */
public class ResourceNotFoundException extends ApiException {

    public ResourceNotFoundException(String message) {
        super(HttpStatus.NOT_FOUND, ApiErrorCode.NOT_FOUND, message);
    }
}
