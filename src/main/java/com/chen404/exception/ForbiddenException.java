package com.chen404.exception;

/**
 * 无权限（如非管理员访问管理接口），由全局异常处理器返回 403
 */
public class ForbiddenException extends RuntimeException {

    public ForbiddenException(String message) {
        super(message);
    }
}
