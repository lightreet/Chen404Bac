package com.chen404.domain.enums;

import com.chen404.exception.BadRequestException;
import lombok.Getter;

/**
 * 阅读笔记低干扰高亮色。
 */
@Getter
public enum ReaderNoteColorEnum {

    ROSE("rose"),
    SAGE("sage"),
    BLUE("blue"),
    AMBER("amber");

    private final String code;

    ReaderNoteColorEnum(String code) {
        this.code = code;
    }

    /**
     * 将接口颜色值规范化为受支持的预设色。
     *
     * @param value 接口颜色值
     * @return 规范化后的颜色编码
     */
    public static String normalize(String value) {
        if (value != null) {
            for (ReaderNoteColorEnum item : values()) {
                if (item.code.equalsIgnoreCase(value.strip())) {
                    return item.code;
                }
            }
        }
        throw new BadRequestException("笔记高亮色仅支持 rose、sage、blue 或 amber");
    }
}
