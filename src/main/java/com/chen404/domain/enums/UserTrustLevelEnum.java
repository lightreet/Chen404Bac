package com.chen404.domain.enums;

import lombok.Getter;

@Getter
public enum UserTrustLevelEnum {

    NORMAL(0, "读者"),
    FRIEND(1, "知友");

    private final int level;
    private final String displayName;

    UserTrustLevelEnum(int level, String displayName) {
        this.level = level;
        this.displayName = displayName;
    }

    public static UserTrustLevelEnum fromLevel(Integer level) {
        if (level != null) {
            for (UserTrustLevelEnum value : values()) {
                if (value.level == level) {
                    return value;
                }
            }
        }
        return NORMAL;
    }

    public static boolean isValidLevel(Integer level) {
        if (level == null) {
            return false;
        }
        for (UserTrustLevelEnum value : values()) {
            if (value.level == level) {
                return true;
            }
        }
        return false;
    }
}
