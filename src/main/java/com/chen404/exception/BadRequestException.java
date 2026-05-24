package com.chen404.exception;

import com.chen404.domain.ApiErrorCode;
import org.springframework.http.HttpStatus;

/**
 * 请求参数或上传内容不合法，由全局异常处理器返回 400
 */
public class BadRequestException extends ApiException {

    public BadRequestException(String message) {
        super(HttpStatus.BAD_REQUEST, ApiErrorCode.BAD_REQUEST, message);
    }
}
