package com.chen404.domain.enums;

import lombok.Getter;

@Getter
public enum ArticleVisibilityEnum {

    PUBLIC(0),
    LOGIN(1),
    FRIEND(2),
    PRIVATE(3);

    private final int value;

    ArticleVisibilityEnum(int value) {
        this.value = value;
    }

    public static ArticleVisibilityEnum fromValue(Integer value) {
        if (value != null) {
            for (ArticleVisibilityEnum item : values()) {
                if (item.value == value) {
                    return item;
                }
            }
        }
        return PUBLIC;
    }
}
