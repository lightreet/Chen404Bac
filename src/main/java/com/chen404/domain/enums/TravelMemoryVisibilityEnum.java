package com.chen404.domain.enums;

import lombok.Getter;

/**
 * 旅行记忆地点可见范围。
 */
@Getter
public enum TravelMemoryVisibilityEnum {

    PUBLIC(0, "公开"),
    FRIEND(2, "知友可见");

    private final int value;
    private final String displayName;

    TravelMemoryVisibilityEnum(int value, String displayName) {
        this.value = value;
        this.displayName = displayName;
    }

    public static TravelMemoryVisibilityEnum fromValue(Integer value) {
        if (value != null) {
            for (TravelMemoryVisibilityEnum item : values()) {
                if (item.value == value) {
                    return item;
                }
            }
        }
        return FRIEND;
    }

    public static int normalizeValue(Integer value) {
        return fromValue(value).value;
    }
}
