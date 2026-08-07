package com.chen404.domain;

/**
 * API 响应码常量，默认与 HTTP 状态码保持一致。
 */
public final class ApiErrorCode {

    public static final int SUCCESS = 200;
    public static final int BAD_REQUEST = 400;
    public static final int UNAUTHORIZED = 401;
    public static final int FORBIDDEN = 403;
    public static final int NOT_FOUND = 404;
    public static final int CONFLICT = 409;
    public static final int VALIDATION_FAILED = 422;
    public static final int TOO_MANY_REQUESTS = 429;
    public static final int INTERNAL_SERVER_ERROR = 500;

    private ApiErrorCode() {
    }
}
