package com.chen404.domain.dto;

/** 文章列表的筛选数据，不携带实体写入字段或访问者权限。 */
public record ArticleSearchCriteria(Integer status, Long categoryId, Long tagId, Long authorId, String keyword) {
}
