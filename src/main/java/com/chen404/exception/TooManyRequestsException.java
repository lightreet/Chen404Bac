package com.chen404.exception;

/**
 * 请求频率过高。
 */
public class TooManyRequestsException extends RuntimeException {

    public TooManyRequestsException(String message) {
        super(message);
    }
}
