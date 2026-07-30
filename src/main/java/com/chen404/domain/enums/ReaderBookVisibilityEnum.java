package com.chen404.domain.enums;

import com.chen404.exception.BadRequestException;
import lombok.Getter;

/**
 * 小说书籍的可见范围。
 */
@Getter
public enum ReaderBookVisibilityEnum {

    PUBLIC("public"),
    PRIVATE("private");

    private final String code;

    ReaderBookVisibilityEnum(String code) {
        this.code = code;
    }

    /**
     * 将接口传入的可见范围规范化为已支持的领域值。
     */
    public static String normalize(String value) {
        if (value == null || value.isBlank()) {
            return PRIVATE.code;
        }
        for (ReaderBookVisibilityEnum item : values()) {
            if (item.code.equalsIgnoreCase(value.strip())) {
                return item.code;
            }
        }
        throw new BadRequestException("书籍可见范围仅支持 public 或 private");
    }
}
