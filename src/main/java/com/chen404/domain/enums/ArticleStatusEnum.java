package com.chen404.domain.enums;

import lombok.Getter;

@Getter
public enum ArticleStatusEnum {

    DRAFT(0),
    PUBLISHED(1),
    RECYCLED(2);

    private final int value;

    ArticleStatusEnum(int value) {
        this.value = value;
    }

    public static boolean is(Integer value, ArticleStatusEnum status) {
        return status != null && value != null && status.value == value;
    }
}
