package com.chen404.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.IService;
import com.chen404.domain.dto.ArchiveYearVO;
import com.chen404.domain.dto.ArticleLikeResult;
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
    Page<Article> getArticlePage(Integer page, Integer size, Integer status, Long categoryId, Long tagId, Long authorId, String keyword, Long requesterId);

    /**
     * 管理端：分页查询当前用户的文章列表（可按状态筛选）
     */
    Page<Article> getMyArticlePage(Long userId, Integer page, Integer size, Integer status, String keyword);

    /**
     * 获取文章详情
     */
    Article getArticleById(Long id, boolean incrementView, Long requesterId);

    /**
     * 获取上一篇、下一篇文章（仅 id、title，按发布时间排序）
     */
    Map<String, Article> getNeighbors(Long articleId, Long requesterId);

    /**
     * 创建文章
     */
    Article createArticle(Article article);

    /**
     * 更新文章
     */
    Article updateArticle(Long id, Article article, Long operatorId);

    /**
     * 删除文章
     */
    void deleteArticle(Long id, Long operatorId);

    /**
     * 点赞文章：匿名每次 +1（限流）；登录用户为切换赞/取消
     */
    ArticleLikeResult likeArticle(Long id, Long requesterId, String clientIp);

    /**
     * 切换收藏（需登录）
     */
    boolean toggleFavorite(Long articleId, Long userId);

    /**
     * 个人中心：我点赞过的文章（仅仍可见的）
     */
    Page<Article> getMyLikedArticlePage(Long userId, Integer page, Integer size);

    /**
     * 个人中心：我的收藏
     */
    Page<Article> getMyFavoriteArticlePage(Long userId, Integer page, Integer size);

    /**
     * 获取热门文章
     */
    List<Article> getHotArticles(Integer limit, Long requesterId);

    /**
     * 获取推荐文章
     */
    List<Article> getRecommendArticles(Integer limit, Long requesterId);

    /**
     * 获取站点统计
     */
    Map<String, Object> getSiteStats();

    /**
     * 归档：按年、月分组的公开已发布文章时间线
     */
    List<ArchiveYearVO> listArchives(Long requesterId);
}
