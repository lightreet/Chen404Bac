package com.chen404.domain.enums;

import lombok.Getter;

@Getter
public enum ArticleCommentPolicyEnum {

    CLOSED(0),
    REGISTERED(1),
    FRIEND(2),
    PUBLIC(3);

    private final int value;

    ArticleCommentPolicyEnum(int value) {
        this.value = value;
    }

    public static ArticleCommentPolicyEnum fromValue(Integer value) {
        if (value != null) {
            for (ArticleCommentPolicyEnum item : values()) {
                if (item.value == value) {
                    return item;
                }
            }
        }
        return REGISTERED;
    }
}
