package com.chen404.domain.enums;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;
import java.util.Locale;

/** 单张手机上传的状态；保留原有小写 JSON 值以兼容已存在的短时 Redis 会话。 */
public enum TravelMobilePhotoStatus {
    UPLOADING, DONE, FAILED;

    @JsonValue
    public String getCode() {
        return name().toLowerCase(Locale.ROOT);
    }

    @JsonCreator
    public static TravelMobilePhotoStatus fromCode(String code) {
        return valueOf(code.toUpperCase(Locale.ROOT));
    }
}
