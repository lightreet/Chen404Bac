package com.chen404.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.IService;
import com.chen404.domain.entity.Article;

import java.util.List;
import java.util.Map;

/**
 * 文章服务接口
 */
public interface ArticleService extends IService<Article> {

    /**
     * 分页查询文章列表
     */
    Page<Article> getArticlePage(Integer page, Integer size, Integer status, Long categoryId, Long tagId, String keyword);

    /**
     * 获取文章详情
     */
    Article getArticleById(Long id, boolean incrementView);

    /**
     * 创建文章
     */
    Article createArticle(Article article);

    /**
     * 更新文章
     */
    Article updateArticle(Long id, Article article);

    /**
     * 删除文章
     */
    void deleteArticle(Long id);

    /**
     * 点赞文章
     */
    Integer likeArticle(Long id);

    /**
     * 获取热门文章
     */
    List<Article> getHotArticles(Integer limit);

    /**
     * 获取推荐文章
     */
    List<Article> getRecommendArticles(Integer limit);

    /**
     * 获取站点统计
     */
    Map<String, Object> getSiteStats();
}
