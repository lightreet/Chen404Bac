package com.chen404.domain.enums;

import lombok.Getter;

/**
 * 旅行纪念地点展示状态枚举。
 */
@Getter
public enum TravelMemoryStatusEnum {

    HIDDEN(0, "隐藏"),
    VISIBLE(1, "展示");

    private final int value;
    private final String displayName;

    TravelMemoryStatusEnum(int value, String displayName) {
        this.value = value;
        this.displayName = displayName;
    }

    public static int normalizeValue(Integer rawValue) {
        if (rawValue != null) {
            for (TravelMemoryStatusEnum value : values()) {
                if (value.value == rawValue) {
                    return value.value;
                }
            }
        }
        return VISIBLE.value;
    }
}
