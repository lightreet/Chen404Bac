package com.chen404.exception;

/**
 * 资源不存在时抛出，由全局异常处理器返回 404。
 */
public class ResourceNotFoundException extends RuntimeException {

    public ResourceNotFoundException(String message) {
        super(message);
    }
}
