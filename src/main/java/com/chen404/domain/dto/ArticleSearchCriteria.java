package com.chen404.domain.dto;

/** 文章列表的筛选数据，不携带实体写入字段或访问者权限。 */
public record ArticleSearchCriteria(Integer status, Long categoryId, Long tagId, Long authorId, String keyword) {
    /** 保留原先 Java 查询的空白语义，包括全角空格；非空关键词不裁剪。 */
    public ArticleSearchCriteria {
        if (keyword != null && keyword.isBlank()) {
            keyword = null;
        }
    }
}
