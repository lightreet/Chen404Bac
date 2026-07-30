package com.chen404.domain.enums;

import com.chen404.exception.BadRequestException;
import lombok.Getter;

/**
 * 小说书籍的可见范围。
 */
@Getter
public enum ReaderBookVisibilityEnum {

    PUBLIC("public"),
    FRIEND("friend"),
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
            return PUBLIC.code;
        }
        for (ReaderBookVisibilityEnum item : values()) {
            if (item.code.equalsIgnoreCase(value.strip())) {
                return item.code;
            }
        }
        throw new BadRequestException("书籍可见范围仅支持 public、friend 或 private");
    }

    /**
     * 从持久化值读取权限；遇到历史脏值时按私密处理，避免意外暴露书籍。
     */
    public static ReaderBookVisibilityEnum fromCode(String value) {
        if (value != null) {
            for (ReaderBookVisibilityEnum item : values()) {
                if (item.code.equalsIgnoreCase(value.strip())) {
                    return item;
                }
            }
        }
        return PRIVATE;
    }
}
