package com.chen404.service;

import com.chen404.domain.PageResult;
import com.chen404.domain.dto.ArchiveYearVO;
import com.chen404.domain.dto.ArticleDetailVO;
import com.chen404.domain.dto.ArticleLikeResult;
import com.chen404.domain.dto.ArticleListItemVO;
import com.chen404.domain.dto.ArticleNeighborsVO;
import com.chen404.domain.dto.CreateArticleCommand;
import com.chen404.domain.dto.UpdateArticleCommand;

import java.util.Collection;
import java.util.List;
import java.util.Map;

/**
 * 文章服务接口
 */
public interface ArticleService {

    /**
     * 分页查询文章列表
     */
    PageResult<ArticleListItemVO> getArticlePage(Integer page, Integer size, Integer status, Long categoryId, Long tagId, Long authorId, String keyword, Long requesterId);

    /**
     * 管理端：分页查询当前用户的文章列表（可按状态筛选）
     */
    PageResult<ArticleListItemVO> getMyArticlePage(Long userId, Integer page, Integer size, Integer status, String keyword);

    /**
     * 获取文章详情
     */
    ArticleDetailVO getArticleById(Long id, boolean incrementView, Long requesterId);

    /**
     * 获取上一篇、下一篇文章（仅 id、title，按发布时间排序）
     */
    ArticleNeighborsVO getNeighbors(Long articleId, Long requesterId);

    /**
     * 创建文章
     */
    ArticleDetailVO createArticle(CreateArticleCommand command, Long operatorId);

    /**
     * 更新文章
     */
    ArticleDetailVO updateArticle(Long id, UpdateArticleCommand command, Long operatorId);

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
    PageResult<ArticleListItemVO> getMyLikedArticlePage(Long userId, Integer page, Integer size);

    /**
     * 个人中心：我的收藏
     */
    PageResult<ArticleListItemVO> getMyFavoriteArticlePage(Long userId, Integer page, Integer size);

    /**
     * 获取热门文章
     */
    List<ArticleListItemVO> getHotArticles(Integer limit, Long requesterId);

    /**
     * 获取推荐文章
     */
    List<ArticleListItemVO> getRecommendArticles(Integer limit, Long requesterId);

    /**
     * 获取站点统计
     */
    Map<String, Object> getSiteStats();

    /**
     * 归档：按年、月分组的公开已发布文章时间线
     */
    List<ArchiveYearVO> listArchives(Long requesterId);
    /** 按当前访问权限批量返回标题，不暴露实体或通用查询构造器。 */
    Map<Long, String> getVisibleArticleTitles(Collection<Long> articleIds, Long requesterId);
}
