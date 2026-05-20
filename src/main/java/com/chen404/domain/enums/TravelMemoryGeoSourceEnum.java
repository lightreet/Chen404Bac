package com.chen404.domain.enums;

import lombok.Getter;

/**
 * 旅行纪念照片坐标来源枚举。
 */
@Getter
public enum TravelMemoryGeoSourceEnum {

    NONE("NONE", "未提供"),
    EXIF("EXIF", "照片 EXIF"),
    MANUAL("MANUAL", "手动选择");

    private final String code;
    private final String displayName;

    TravelMemoryGeoSourceEnum(String code, String displayName) {
        this.code = code;
        this.displayName = displayName;
    }

    public static String normalizeCode(String rawCode) {
        if (rawCode != null) {
            for (TravelMemoryGeoSourceEnum value : values()) {
                if (value.code.equalsIgnoreCase(rawCode.trim())) {
                    return value.code;
                }
            }
        }
        return NONE.code;
    }
}
