package com.chen404.domain;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.io.Serializable;

/**
 * 统一响应结果
 */
@Schema(description = "统一响应结果")
@Data
public class Result<T> implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 状态码
     */
    @Schema(description = "状态码，200表示成功", example = "200")
    private Integer code;

    /**
     * 提示信息
     */
    @Schema(description = "提示信息", example = "操作成功")
    private String message;

    /**
     * 数据
     */
    @Schema(description = "响应数据")
    private T data;

    public Result() {
    }

    public Result(Integer code, String message, T data) {
        this.code = code;
        this.message = message;
        this.data = data;
    }

    /**
     * 成功响应
     */
    public static <T> Result<T> success() {
        return new Result<>(ApiErrorCode.SUCCESS, "success", null);
    }

    public static <T> Result<T> success(String message) {
        return new Result<>(ApiErrorCode.SUCCESS, message, null);
    }

    public static <T> Result<T> success(T data) {
        return new Result<>(ApiErrorCode.SUCCESS, "success", data);
    }

    public static <T> Result<T> success(String message, T data) {
        return new Result<>(ApiErrorCode.SUCCESS, message, data);
    }

    /**
     * 错误响应
     */
    public static <T> Result<T> error() {
        return new Result<>(ApiErrorCode.INTERNAL_SERVER_ERROR, "error", null);
    }

    public static <T> Result<T> error(String message) {
        return new Result<>(ApiErrorCode.INTERNAL_SERVER_ERROR, message, null);
    }

    public static <T> Result<T> error(Integer code, String message) {
        return new Result<>(code, message, null);
    }
}
