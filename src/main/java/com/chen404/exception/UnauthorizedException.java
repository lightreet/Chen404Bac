package com.chen404.exception;

/**
 * 未登录或 Token 无效时抛出，由全局异常处理器返回 401
 */
public class UnauthorizedException extends RuntimeException {

    public UnauthorizedException() {
        super("未登录");
    }

    public UnauthorizedException(String message) {
        super(message);
    }
}
