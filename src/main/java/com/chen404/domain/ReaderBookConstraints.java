package com.chen404.domain;

/** 预览、创建导入任务和后台落库共用的书籍字段约束。 */
public final class ReaderBookConstraints {
    public static final int TITLE_MAX_LENGTH = 255;
    public static final int AUTHOR_MAX_LENGTH = 255;
    public static final int LANGUAGE_MAX_LENGTH = 40;
    public static final int DESCRIPTION_MAX_LENGTH = 4_000;
    public static final int PARSE_MESSAGE_MAX_LENGTH = 1_000;
    /** 章节、卷标题和目录标签使用相同的标题限制。 */
    public static final int HEADING_MAX_LENGTH = 500;
    /** 归档内资源路径与章节、目录来源路径使用相同的存储限制。 */
    public static final int SOURCE_PATH_MAX_LENGTH = 1_000;

    private ReaderBookConstraints() {
    }
}
