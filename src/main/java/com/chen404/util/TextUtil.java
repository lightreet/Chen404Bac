package com.chen404.util;

/** 无业务副作用的文本长度处理；不隐式修剪空格或追加省略号。 */
public final class TextUtil {
    private TextUtil() {
    }

    /** 保持已有 Java 字符长度限制与 null 语义。 */
    public static String truncate(String value, int maxLength) {
        if (maxLength < 0) {
            throw new IllegalArgumentException("文本长度上限不能为负数");
        }
        return value == null || value.length() <= maxLength ? value : value.substring(0, maxLength);
    }
}
