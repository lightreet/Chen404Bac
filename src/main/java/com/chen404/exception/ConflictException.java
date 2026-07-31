package com.chen404.exception;

import com.chen404.domain.ApiErrorCode;
import org.springframework.http.HttpStatus;

/**
 * 当前资源状态与请求操作冲突时抛出，由全局异常处理器返回 409。
 */
public class ConflictException extends ApiException {

    public ConflictException(String message) {
        super(HttpStatus.CONFLICT, ApiErrorCode.CONFLICT, message);
    }
}
