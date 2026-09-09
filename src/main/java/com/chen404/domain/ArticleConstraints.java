package com.chen404.domain;

/** 文章创建、编辑及内部写入需要保持一致的约束。 */
public final class ArticleConstraints {
    public static final int TITLE_MAX_LENGTH = 100;
    public static final int SUMMARY_MAX_LENGTH = 500;
    public static final String INVALID_STATUS = "文章状态无效";
    public static final String INVALID_VISIBILITY = "文章可见性无效";
    public static final String INVALID_COMMENT_POLICY = "文章评论策略无效";

    private ArticleConstraints() {
    }
}
