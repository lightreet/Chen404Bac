package com.chen404.exception;

/**
 * 请求参数或上传内容不合法，由全局异常处理器返回 400
 */
public class BadRequestException extends RuntimeException {

    public BadRequestException(String message) {
        super(message);
    }
}
