package com.chen404.domain.enums;

import lombok.Getter;

/**
 * 阅读器章节衔接方式。
 */
@Getter
public enum ReaderReadingModeEnum {

    PAGED("paged"),
    CONTINUOUS("continuous");

    private final String code;

    ReaderReadingModeEnum(String code) {
        this.code = code;
    }
}
