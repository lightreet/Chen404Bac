package com.chen404.domain;

/** 预览、创建导入任务和后台落库共用的书籍字段约束。 */
public final class ReaderBookConstraints {
    public static final int TITLE_MAX_LENGTH = 255;
    public static final int AUTHOR_MAX_LENGTH = 255;
    public static final int DESCRIPTION_MAX_LENGTH = 4_000;
    public static final int PARSE_MESSAGE_MAX_LENGTH = 1_000;

    private ReaderBookConstraints() {
    }
}
